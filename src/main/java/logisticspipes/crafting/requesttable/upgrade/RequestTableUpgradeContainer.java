package logisticspipes.crafting.requesttable.upgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableUpgradeStatePacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.DummyContainer;
import lombok.Getter;

/** Per-viewer material escrow; completed tiers live on the table, never on this container. */
public class RequestTableUpgradeContainer extends DummyContainer {

    public static final int MATERIAL_SLOTS = 9;
    public static final int INVENTORY_LEFT = 62;
    @Getter
    private final RequestTablePipe table;
    private final EntityPlayer player;
    private final InventoryBasic materials = new InventoryBasic("Upgrade materials", false, MATERIAL_SLOTS);
    private final List<Slot> materialSlots = new ArrayList<>();
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
    private boolean materialsReturned;

    public RequestTableUpgradeContainer(EntityPlayer player, RequestTablePipe table) {
        super(player, null);
        this.player = player;
        this.table = table;
        Arrays.fill(syncedTiers, -1);
        selectedTier = Math.min(RequestTableStorageUpgradeConfig.TIER_COUNT, table.getItemSlotTier() + 1);
        refreshRequirements();
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            Slot slot = addSlotToContainer(new Slot(materials, i, 0, 0) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return matchesMaterial(stack);
                }
            });
            materialSlots.add(slot);
        }
        addNormalSlotsForPlayerInventory(0, 0);
    }

    public RequestTableStorageUpgradeConfig getConfig() {
        return getConfig(selectedBranch.isFluid());
    }

    public RequestTableStorageUpgradeConfig getConfig(boolean fluid) {
        return fluid ? fluidConfig : itemConfig;
    }

    private void refreshRequirements() {
        requirements = RequestTableUpgradeMaterials.getRecipe(selectedBranch, selectedTier, getConfig());
    }

    private boolean matchesMaterial(ItemStack stack) {
        for (Requirement requirement : requirements) {
            if (requirement.matches(stack)) return true;
        }
        return false;
    }

    public void awaitState() {
        ready = false;
    }

    public void layout(int guiHeight) {
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            Slot slot = materialSlots.get(i);
            slot.xDisplayPosition = INVENTORY_LEFT + i * 18;
            slot.yDisplayPosition = guiHeight - 115;
        }
        for (int i = 0; i < 36; i++) {
            Slot slot = inventorySlots.get(MATERIAL_SLOTS + i);
            slot.xDisplayPosition = INVENTORY_LEFT + (i % 9) * 18;
            slot.yDisplayPosition = guiHeight - 84 + (i < 27 ? (i / 9) * 18 : 58);
        }
    }

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

    public void select(RequestTableUpgradeBranch branch, int tier) {
        if (branch == null || !branch.isValidSelection(tier)) return;
        selectedBranch = branch;
        selectedTier = tier;
        refreshRequirements();
        stateDirty = true;
        detectAndSendChanges();
    }

    public int getMaterialCount(Requirement requirement) {
        int count = 0;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack stack = materials.getStackInSlot(i);
            if (requirement.matches(stack)) count += stack.stackSize;
        }
        return count;
    }

    public boolean canUpgrade() {
        return hasPrerequisites() && planConsumption() != null;
    }

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

    public void startUpgrade(RequestTableUpgradeBranch branch, int tier, boolean creative) {
        if (MainProxy.isClient(player.worldObj) || branch != selectedBranch
            || tier != selectedTier
            || !canInteractWith(player)
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
        detectAndSendChanges();
    }

    @Override
    public boolean canInteractWith(EntityPlayer viewer) {
        return viewer == player && PacketGuards.canConfigurePipe(viewer, table);
    }

    @Override
    public void detectAndSendChanges() {
        if (MainProxy.isClient(player.worldObj)) return;
        super.detectAndSendChanges();
        boolean changed = false;
        for (RequestTableUpgradeBranch branch : RequestTableUpgradeBranch.values()) {
            changed |= syncedTiers[branch.ordinal()] != table.getUpgradeTier(branch);
        }
        if (!stateDirty && !changed) return;
        for (ICrafting crafter : crafters) {
            if (crafter instanceof EntityPlayer viewer) {
                MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(RequestTableUpgradeStatePacket.class).setState(this),
                    viewer);
            }
        }
        for (RequestTableUpgradeBranch branch : RequestTableUpgradeBranch.values()) {
            syncedTiers[branch.ordinal()] = table.getUpgradeTier(branch);
        }
        stateDirty = false;
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafter) {
        stateDirty = true;
        super.addCraftingToCrafters(crafter);
    }

    @Override
    public void onContainerClosed(EntityPlayer viewer) {
        super.onContainerClosed(viewer);
        if (MainProxy.isClient(viewer.worldObj) || materialsReturned) return;
        materialsReturned = true;
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            ItemStack stack = materials.getStackInSlotOnClosing(i);
            if (stack != null && (!viewer.isEntityAlive() || !viewer.inventory.addItemStackToInventory(stack))) {
                viewer.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
        viewer.inventory.markDirty();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer viewer, int index) {
        if (index < 0 || index >= inventorySlots.size()) return null;
        Slot source = inventorySlots.get(index);
        if (!source.getHasStack() || !source.canTakeStack(viewer)) return null;
        ItemStack moving = source.getStack();
        ItemStack original = moving.copy();
        if (index < MATERIAL_SLOTS) {
            if (!mergeItemStack(moving, MATERIAL_SLOTS, inventorySlots.size(), true)) return null;
        } else {
            if (!matchesMaterial(moving) || !mergeItemStack(moving, 0, MATERIAL_SLOTS, false)) return null;
        }
        source.putStack(moving.stackSize == 0 ? null : moving);
        source.onPickupFromSlot(viewer, moving);
        source.onSlotChanged();
        return original;
    }

    @Override
    protected void retrySlotClick(int slot, int button, boolean shift, EntityPlayer viewer) {
        // The merge already fills every available destination; a partial transfer needs no retry.
    }
}
