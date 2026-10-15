package logisticspipes.crafting.requesttable.upgrade;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import lombok.Getter;

/**
 * One viewer's upgrade selection and material escrow. Completed tiers live on the table, never in the session.
 * <p>
 * The server validates and consumes the materials; the client copy only mirrors the state for rendering.
 */
public class RequestTableUpgradeSession implements RequestTableUpgradeState {

    public static final int MATERIAL_SLOTS = 9;

    @Getter
    private final RequestTablePipe table;
    private final EntityPlayer player;
    @Getter
    private final InventoryBasic materials = new InventoryBasic("Upgrade materials", false, MATERIAL_SLOTS);
    private final int[] syncedTiers = new int[RequestTableUpgradeBranch.values().length];
    private RequestTableStorageUpgradeConfig itemConfig = RequestTableStorageUpgradeConfig.getConfigured(false);
    private RequestTableStorageUpgradeConfig fluidConfig = RequestTableStorageUpgradeConfig.getConfigured(true);
    @Getter
    private int selectedTier;
    @Getter
    private RequestTableUpgradeBranch selectedBranch = RequestTableUpgradeBranch.SLOT_COUNT;
    @Getter
    private List<Requirement> requirements;
    private boolean stateDirty = true;
    @Getter
    private boolean ready;

    public RequestTableUpgradeSession(EntityPlayer player, RequestTablePipe table) {
        this.player = player;
        this.table = table;
        Arrays.fill(syncedTiers, -1);
        selectedTier = Math.min(RequestTableStorageUpgradeConfig.TIER_COUNT, table.getItemSlotTier() + 1);
        refreshRequirements();
    }

    @Override
    public RequestTableStorageUpgradeConfig getConfig(boolean fluid) {
        return fluid ? fluidConfig : itemConfig;
    }

    private void refreshRequirements() {
        requirements = RequestTableUpgradeMaterials.getRecipe(selectedBranch, selectedTier, getConfig());
    }

    public boolean matchesMaterial(ItemStack stack) {
        for (Requirement requirement : requirements) {
            if (requirement.matches(stack)) return true;
        }
        return false;
    }

    /** Marks the client copy as outdated until the server answers its next selection. */
    public void awaitState() {
        ready = false;
    }

    /** Applies the server's state to the client copy. */
    public void applyState(RequestTableStorageUpgradeConfig itemConfig, RequestTableStorageUpgradeConfig fluidConfig,
            int slotTier, int sizeTier, int fluidSlotTier, int fluidSizeTier, boolean fluidController, boolean monitor,
            RequestTableUpgradeBranch branch, int tier) {
        this.itemConfig = itemConfig;
        this.fluidConfig = fluidConfig;
        table.applyItemUpgradeTiers(slotTier, sizeTier);
        table.applyFluidUpgradeTiers(fluidSlotTier, fluidSizeTier);
        table.applySpecialUpgrades(fluidController, monitor);
        selectedBranch = branch;
        selectedTier = tier;
        refreshRequirements();
        ready = true;
    }

    /** Selects an upgrade on the server; the state is sent with the next change check. */
    public void select(RequestTableUpgradeBranch branch, int tier) {
        if (branch == null || !branch.isValidSelection(tier)) return;
        selectedBranch = branch;
        selectedTier = tier;
        refreshRequirements();
        stateDirty = true;
    }

    @Override
    public int getMaterialCount(Requirement requirement) {
        int count = 0;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack stack = materials.getStackInSlot(i);
            if (requirement.matches(stack)) count += stack.stackSize;
        }
        return count;
    }

    @Override
    public boolean canUpgrade() {
        return hasPrerequisites() && planConsumption() != null;
    }

    @Override
    public boolean canUpgradeCreative() {
        return player.capabilities.isCreativeMode && hasPrerequisites();
    }

    private boolean hasPrerequisites() {
        if (selectedBranch.isSpecial()) return table.getUpgradeTier(selectedBranch) == 0;
        return table.getUpgradeTier(selectedBranch) + 1 == selectedTier
                && (!selectedBranch.isFluid() || table.isFluidEnabled());
    }

    /** Reserve each input only once, even if two configured requirements happen to overlap. */
    private int[] planConsumption() {
        int[] consumed = new int[MATERIAL_SLOTS];
        for (Requirement requirement : requirements) {
            int remaining = requirement.getCount();
            for (int i = 0; i < MATERIAL_SLOTS && remaining > 0; i++) {
                ItemStack stack = materials.getStackInSlot(i);
                if (!requirement.matches(stack)) continue;
                int taken = Math.min(remaining, stack.stackSize - consumed[i]);
                consumed[i] += taken;
                remaining -= taken;
            }
            if (remaining > 0) return null;
        }
        return consumed;
    }

    public boolean canInteract(EntityPlayer viewer) {
        return viewer == player && PacketGuards.canConfigurePipe(viewer, table);
    }

    /** Validates and consumes the selected recipe, then unlocks the upgrade permanently. */
    public void startUpgrade(RequestTableUpgradeBranch branch, int tier, boolean creative) {
        if (player.worldObj.isRemote || branch != selectedBranch
                || tier != selectedTier
                || !canInteract(player)
                || (creative && !player.capabilities.isCreativeMode)
                || !hasPrerequisites())
            return;
        int[] consumed = creative ? new int[MATERIAL_SLOTS] : planConsumption();
        if (consumed == null) return;
        // Both validations run before consumption; another viewer cannot pay twice for the same tier.
        if (branch.isSpecial() ? !table.unlockSpecialUpgrade(branch) : !table.unlockStorageUpgrade(branch, tier))
            return;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            if (consumed[i] > 0) materials.decrStackSize(i, consumed[i]);
        }
        finishUpgradeChange();
    }

    private void finishUpgradeChange() {
        table.updateStorageUpgrades();
        table.container.markDirty();
        table.requestNetworkContentUpdate();
        player.inventory.markDirty();
        player.worldObj.playSoundEffect(
                table.getX() + 0.5,
                table.getY() + 0.5,
                table.getZ() + 0.5,
                "random.anvil_use",
                0.35F,
                1.2F);
        stateDirty = true;
    }

    /** Forces the state to be sent again, for example to a newly added viewer. */
    public void markStateDirty() {
        stateDirty = true;
    }

    /**
     * Returns whether the viewer needs a new state, and records the tiers it is about to receive.
     */
    public boolean consumeStateChange() {
        boolean changed = stateDirty;
        for (RequestTableUpgradeBranch branch : RequestTableUpgradeBranch.values()) {
            changed |= syncedTiers[branch.ordinal()] != table.getUpgradeTier(branch);
        }
        if (!changed) return false;
        for (RequestTableUpgradeBranch branch : RequestTableUpgradeBranch.values()) {
            syncedTiers[branch.ordinal()] = table.getUpgradeTier(branch);
        }
        stateDirty = false;
        return true;
    }

    /** Returns unused materials to the viewer, or drops them when the inventory is full. */
    public void returnMaterials(EntityPlayer viewer) {
        if (viewer.worldObj.isRemote) return;
        boolean returned = false;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack stack = materials.getStackInSlotOnClosing(i);
            if (stack == null) continue;
            returned = true;
            if (!viewer.isEntityAlive() || !viewer.inventory.addItemStackToInventory(stack)) {
                viewer.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
        if (returned) viewer.inventory.markDirty();
    }

    public void writeState(LPDataOutputStream output) throws IOException {
        output.writeInt(table.getItemSlotTier());
        output.writeInt(table.getItemSizeTier());
        output.writeInt(table.getFluidSlotTier());
        output.writeInt(table.getFluidSizeTier());
        output.writeBoolean(table.isFluidEnabled());
        output.writeBoolean(table.hasMonitoringUpgrade());
        output.writeEnum(selectedBranch);
        output.writeInt(selectedTier);
        itemConfig.writeData(output);
        fluidConfig.writeData(output);
    }

    public void readState(LPDataInputStream input) throws IOException {
        int slotTier = input.readInt();
        int sizeTier = input.readInt();
        int fluidSlotTier = input.readInt();
        int fluidSizeTier = input.readInt();
        boolean fluidController = input.readBoolean();
        boolean monitor = input.readBoolean();
        RequestTableUpgradeBranch branch = input.readEnum(RequestTableUpgradeBranch.class);
        int tier = input.readInt();
        RequestTableStorageUpgradeConfig items = RequestTableStorageUpgradeConfig.readData(input);
        RequestTableStorageUpgradeConfig fluids = RequestTableStorageUpgradeConfig.readData(input);
        if (branch == null || !branch.isValidSelection(tier)) return;
        applyState(
                items,
                fluids,
                slotTier,
                sizeTier,
                fluidSlotTier,
                fluidSizeTier,
                fluidController,
                monitor,
                branch,
                tier);
    }
}
