package logisticspipes.crafting.requesttable.upgrade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableUpgradeStatePacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.DummyContainer;

/** Per-viewer material escrow of the legacy upgrade screen; completed tiers live on the table. */
public class RequestTableUpgradeContainer extends DummyContainer implements RequestTableUpgradeState {

    public static final int MATERIAL_SLOTS = RequestTableUpgradeSession.MATERIAL_SLOTS;
    public static final int INVENTORY_LEFT = 62;
    private final RequestTableUpgradeSession session;
    private final EntityPlayer player;
    private final List<Slot> materialSlots = new ArrayList<>();
    private boolean materialsReturned;

    public RequestTableUpgradeContainer(EntityPlayer player, RequestTablePipe table) {
        super(player, null);
        this.player = player;
        session = new RequestTableUpgradeSession(player, table);
        for (int i = 0; i < MATERIAL_SLOTS; i++) {
            Slot slot = addSlotToContainer(new Slot(session.getMaterials(), i, 0, 0) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return session.matchesMaterial(stack);
                }
            });
            materialSlots.add(slot);
        }
        addNormalSlotsForPlayerInventory(0, 0);
    }

    @Override
    public RequestTablePipe getTable() {
        return session.getTable();
    }

    @Override
    public boolean isReady() {
        return session.isReady();
    }

    @Override
    public RequestTableUpgradeBranch getSelectedBranch() {
        return session.getSelectedBranch();
    }

    @Override
    public int getSelectedTier() {
        return session.getSelectedTier();
    }

    @Override
    public List<Requirement> getRequirements() {
        return session.getRequirements();
    }

    @Override
    public RequestTableStorageUpgradeConfig getConfig(boolean fluid) {
        return session.getConfig(fluid);
    }

    @Override
    public int getMaterialCount(Requirement requirement) {
        return session.getMaterialCount(requirement);
    }

    @Override
    public boolean canUpgrade() {
        return session.canUpgrade();
    }

    @Override
    public boolean canUpgradeCreative() {
        return session.canUpgradeCreative();
    }

    public void awaitState() {
        session.awaitState();
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
        session.applyState(
                itemConfig,
                fluidConfig,
                slotTier,
                sizeTier,
                fluidSlotTier,
                fluidSizeTier,
                fluidController,
                monitor,
                branch,
                tier);
    }

    public void select(RequestTableUpgradeBranch branch, int tier) {
        session.select(branch, tier);
        detectAndSendChanges();
    }

    public void startUpgrade(RequestTableUpgradeBranch branch, int tier, boolean creative) {
        session.startUpgrade(branch, tier, creative);
        detectAndSendChanges();
    }

    @Override
    public boolean canInteractWith(EntityPlayer viewer) {
        return session.canInteract(viewer);
    }

    @Override
    public void detectAndSendChanges() {
        if (MainProxy.isClient(player.worldObj)) return;
        super.detectAndSendChanges();
        if (!session.consumeStateChange()) return;
        for (ICrafting crafter : crafters) {
            if (crafter instanceof EntityPlayer viewer) {
                MainProxy.sendPacketToPlayer(
                        PacketHandler.getPacket(RequestTableUpgradeStatePacket.class).setState(this),
                        viewer);
            }
        }
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafter) {
        session.markStateDirty();
        super.addCraftingToCrafters(crafter);
    }

    @Override
    public void onContainerClosed(EntityPlayer viewer) {
        super.onContainerClosed(viewer);
        if (MainProxy.isClient(viewer.worldObj) || materialsReturned) return;
        materialsReturned = true;
        session.returnMaterials(viewer);
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
            if (!session.matchesMaterial(moving) || !mergeItemStack(moving, 0, MATERIAL_SLOTS, false)) return null;
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
