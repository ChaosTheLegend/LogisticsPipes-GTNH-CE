package logisticspipes.pipes.upgrades;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.interfaces.IGuiOpenControler;
import logisticspipes.interfaces.IPipeUpgradeManager;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.items.LogisticsItemCard;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.guis.pipe.UpgradeManagerGui;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.DeprecatedContent;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.item.SimpleStackInventory;
import lombok.Getter;

public class UpgradeManager implements ISimpleInventoryEventHandler, ISlotUpgradeManager, IPipeUpgradeManager {

    @Getter
    private final SimpleStackInventory inv = new SimpleStackInventory(9, "UpgradeInventory", 16);

    private static final String SNEAKY_FACES_KEY = "SneakyFaces";
    @Getter
    private final SimpleStackInventory sneakyInv = new SimpleStackInventory(9, "SneakyUpgradeInventory", 1);

    @Getter
    private final SimpleStackInventory secInv = new SimpleStackInventory(1, "SecurityInventory", 16);

    private final IPipeUpgrade[] upgrades = new IPipeUpgrade[9];
    private final CoreRoutedPipe pipe;
    private int securityDelay = 0;

    /* cached attributes */
    // the faces set in the settings window, in order; they work while a sneaky upgrade is in the slots
    private ForgeDirection[] sneakyFaces = SneakyFaces.NONE;
    private boolean hasSneakyItem = false;
    private int speedUpgradeCount = 0;
    private boolean isAdvancedCrafter = false;
    private boolean hasInstantSatelliteUpgrade = false;
    private boolean isFuzzyUpgrade = false;
    private int liquidCrafter = 0;
    private boolean hasByproductExtractor = false;
    private UUID uuid = null;
    private String uuidS = null;
    private boolean hasPatternUpgrade = false;
    private boolean hasCraftingMonitoringUpgrade = false;
    private int craftingCleanup = 0;
    private boolean hasLogicControll = false;

    private boolean needsContainerPositionUpdate = false;

    public UpgradeManager(CoreRoutedPipe pipe) {
        this.pipe = pipe;
        inv.addListener(this);
        sneakyInv.addListener(this);
        secInv.addListener(this);
    }

    public void readFromNBT(NBTTagCompound nbttagcompound) {
        inv.readFromNBT(nbttagcompound, "UpgradeInventory_");
        sneakyInv.readFromNBT(nbttagcompound, "SneakyUpgradeInventory_");
        secInv.readFromNBT(nbttagcompound, "SecurityInventory_");

        if (sneakyInv.getStackInSlot(8) != null) {
            if (sneakyInv.getStackInSlot(8).getItem() == LogisticsPipes.LogisticsItemCard
                    && sneakyInv.getStackInSlot(8).getItemDamage() == LogisticsItemCard.SEC_CARD) {
                secInv.setInventorySlotContents(0, sneakyInv.getStackInSlot(8));
                sneakyInv.setInventorySlotContents(8, null);
            }
        }
        if (nbttagcompound.hasKey(SNEAKY_FACES_KEY)) {
            sneakyFaces = SneakyFaces.read(nbttagcompound, SNEAKY_FACES_KEY);
        } else {
            // saved before the faces: the old sneaky upgrades give them; the ones in the slots of a combined upgrade
            // stay there and drop when the upgrade window closes or the pipe breaks
            sneakyFaces = SneakyFaces.convertLegacy(inv, sneakyInv);
        }

        InventoryChanged(inv);
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        inv.writeToNBT(nbttagcompound, "UpgradeInventory_");
        sneakyInv.writeToNBT(nbttagcompound, "SneakyUpgradeInventory_");
        secInv.writeToNBT(nbttagcompound, "SecurityInventory_");
        SneakyFaces.write(nbttagcompound, SNEAKY_FACES_KEY, sneakyFaces);
        InventoryChanged(inv);
    }

    private boolean updateModule(int slot, IPipeUpgrade[] upgrades, IInventory inv) {
        // old sneaky upgrades were converted on load (SneakyFaces.convertLegacy) and can't be put in
        upgrades[slot] = LogisticsPipes.UpgradeItem.getUpgradeForItem(inv.getStackInSlot(slot), upgrades[slot]);
        // a connection upgrade becomes a disconnected side of the pipe (decided 2026-11-06), then it is deleted
        if (upgrades[slot] instanceof ConnectionUpgrade connection && pipe != null) {
            pipe.setSideDisconnected(connection.getSide(), true);
        }
        // deprecated upgrades are deleted, not given back (decided 2026-10-30)
        if (upgrades[slot] == null || DeprecatedContent.isDeprecatedUpgrade(inv.getStackInSlot(slot))) {
            upgrades[slot] = null;
            inv.setInventorySlotContents(slot, null);
            return false;
        } else {
            return upgrades[slot].needsUpdate();
        }
    }

    private boolean removeUpgrade(int slot, IPipeUpgrade[] upgrades) {
        boolean needUpdate = upgrades[slot].needsUpdate();
        upgrades[slot] = null;
        return needUpdate;
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        boolean needUpdate = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack item = inv.getStackInSlot(i);
            if (item != null) {
                needUpdate |= updateModule(i, upgrades, inv);
            } else if (item == null && upgrades[i] != null) {
                needUpdate |= removeUpgrade(i, upgrades);
            }
        }
        // update the cached attributes; deprecated upgrades were deleted above
        hasSneakyItem = false;
        speedUpgradeCount = 0;
        isAdvancedCrafter = false;
        hasInstantSatelliteUpgrade = false;
        isFuzzyUpgrade = false;
        liquidCrafter = 0;
        hasByproductExtractor = false;
        hasPatternUpgrade = false;
        hasCraftingMonitoringUpgrade = false;
        craftingCleanup = 0;
        hasLogicControll = false;
        for (int i = 0; i < upgrades.length; i++) {
            IPipeUpgrade upgrade = upgrades[i];
            if (upgrade instanceof SneakyUpgrade) {
                hasSneakyItem = true;
            } else if (upgrade instanceof SpeedUpgrade) {
                speedUpgradeCount += inv.getStackInSlot(i).stackSize;
            } else if (upgrade instanceof AdvancedSatelliteUpgrade) {
                isAdvancedCrafter = true;
            } else if (upgrade instanceof InstantSatelliteUpgrade) {
                hasInstantSatelliteUpgrade = true;
            } else if (upgrade instanceof FuzzyUpgrade) {
                isFuzzyUpgrade = true;
            } else if (upgrade instanceof FluidCraftingUpgrade) {
                liquidCrafter += inv.getStackInSlot(i).stackSize;
            } else if (upgrade instanceof CraftingByproductUpgrade) {
                hasByproductExtractor = true;
            } else if (upgrade instanceof PatternUpgrade) {
                hasPatternUpgrade = true;
            } else if (upgrade instanceof CraftingMonitoringUpgrade) {
                hasCraftingMonitoringUpgrade = true;
            } else if (upgrade instanceof CraftingCleanupUpgrade) {
                craftingCleanup += inv.getStackInSlot(i).stackSize;
            } else if (upgrade instanceof LogicControllerUpgrade) {
                hasLogicControll = true;
            }
        }
        liquidCrafter = Math.min(liquidCrafter, ItemUpgrade.MAX_LIQUID_CRAFTER);
        craftingCleanup = Math.min(craftingCleanup, ItemUpgrade.MAX_CRAFTING_CLEANUP);
        if (needUpdate) {
            pipe.connectionUpdate();
            if (pipe.container != null) {
                pipe.container.sendUpdateToClient();
            }
        }
        uuid = null;
        uuidS = null;
        ItemStack stack = secInv.getStackInSlot(0);
        if (stack == null) {
            return;
        }
        if (stack.getItem() != LogisticsPipes.LogisticsItemCard
                || stack.getItemDamage() != LogisticsItemCard.SEC_CARD) {
            return;
        }
        if (!stack.hasTagCompound()) {
            return;
        }
        if (!stack.getTagCompound().hasKey("UUID")) {
            return;
        }
        uuid = UUID.fromString(stack.getTagCompound().getString("UUID"));
        uuidS = uuid.toString();
    }

    /* Special implementations */

    @Override
    public boolean hasSneakyUpgrade() {
        return getSneakyOrientations().length > 0;
    }

    @Override
    public ForgeDirection getSneakyOrientation() {
        return hasSneakyUpgrade() ? sneakyFaces[0] : ForgeDirection.UNKNOWN;
    }

    @Override
    public ForgeDirection[] getSneakyOrientations() {
        return hasSneakyItem ? sneakyFaces : SneakyFaces.NONE;
    }

    @Override
    public boolean hasSneakyItem() {
        return hasSneakyItem;
    }

    @Override
    public ForgeDirection[] getSneakyFaces() {
        return sneakyFaces;
    }

    @Override
    public void setSneakyFaces(ForgeDirection[] faces) {
        sneakyFaces = faces == null ? SneakyFaces.NONE : faces;
    }

    @Override
    public int getSpeedUpgradeCount() {
        return speedUpgradeCount;
    }

    /**
     * @return false: the combined sneaky upgrade is merged into the sneaky upgrade, the old windows show no sneaky
     *         slots
     */
    @Override
    public boolean hasCombinedSneakyUpgrade() {
        return false;
    }

    @Override
    public IItemHandlerModifiable getUpgradeInventory() {
        return new InvWrapper(inv);
    }

    public void openGui(EntityPlayer entityplayer, CoreRoutedPipe pipe) {
        NewGuiHandler.getGui(UpgradeManagerGui.class).setTilePos(pipe.container).open(entityplayer);
    }

    public IGuiOpenControler getGuiController() {
        return new IGuiOpenControler() {

            final PlayerCollectionList players = new PlayerCollectionList();

            @Override
            public void guiOpenedByPlayer(EntityPlayer player) {
                players.add(player);
            }

            @Override
            public void guiClosedByPlayer(EntityPlayer player) {
                players.remove(player);
                if (players.isEmpty()) {
                    sneakyInv.dropContents(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
                }
            }
        };
    }

    public DummyContainer getDummyContainer(EntityPlayer player) {
        DummyContainer dummy = new DummyContainer(player, inv, getGuiController());
        dummy.addNormalSlotsForPlayerInventory(8, 60);

        // Pipe slots
        for (int pipeSlot = 0; pipeSlot < 8; pipeSlot++) {
            int slot = pipeSlot;
            dummy.addRestrictedSlot(pipeSlot, inv, 8 + pipeSlot * 18, 18, stack -> isUpgradeAllowed(slot, stack));
        }
        // Static slot for Security Cards
        dummy.addStaticRestrictedSlot(0, secInv, 8 + 8 * 18, 18, itemStack -> {
            if (itemStack == null) {
                return false;
            }
            if (itemStack.getItem() != LogisticsPipes.LogisticsItemCard) {
                return false;
            }
            if (itemStack.getItemDamage() != LogisticsItemCard.SEC_CARD) {
                return false;
            }
            return SimpleServiceLocator.securityStationManager
                    .isAuthorized(UUID.fromString(itemStack.getTagCompound().getString("UUID")));
        }, 1);

        // the slots of the old combined sneaky upgrade, hidden: what is left in them drops when the window closes
        int y = 100000;
        for (int pipeSlot = 0; pipeSlot < 9; pipeSlot++) {
            dummy.addRestrictedSlot(pipeSlot, sneakyInv, 8 + pipeSlot * 18, y, itemStack -> {
                if (itemStack == null) {
                    return false;
                }
                if (itemStack.getItem() == LogisticsPipes.UpgradeItem) {
                    IPipeUpgrade upgrade = LogisticsPipes.UpgradeItem.getUpgradeForItem(itemStack, null);
                    if (!(upgrade instanceof SneakyUpgrade)) {
                        return false;
                    }
                    return upgrade.isAllowedForPipe(pipe);
                } else {
                    return false;
                }
            });
        }
        return dummy;
    }

    public boolean isNeedingContainerUpdate() {
        boolean tmp = needsContainerPositionUpdate;
        needsContainerPositionUpdate = false;
        return tmp;
    }

    public void dropUpgrades() {
        inv.dropContents(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
        sneakyInv.dropContents(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
    }

    public boolean tryIserting(World world, EntityPlayer entityplayer) {
        if (entityplayer.getCurrentEquippedItem() != null
                && entityplayer.getCurrentEquippedItem().getItem() == LogisticsPipes.UpgradeItem) {
            if (MainProxy.isClient(world)) {
                return true;
            }
            IPipeUpgrade upgrade = LogisticsPipes.UpgradeItem
                    .getUpgradeForItem(entityplayer.getCurrentEquippedItem(), null);
            if (upgrade != null && upgrade.isAllowedForPipe(pipe)) {
                if (insertIntInv(entityplayer, inv)) {
                    return true;
                }
            }
        }
        if (entityplayer.getCurrentEquippedItem() != null
                && entityplayer.getCurrentEquippedItem().getItem() == LogisticsPipes.LogisticsItemCard
                && entityplayer.getCurrentEquippedItem().getItemDamage() == LogisticsItemCard.SEC_CARD) {
            if (MainProxy.isClient(world)) {
                return true;
            }
            if (secInv.getStackInSlot(0) == null) {
                ItemStack newItem = entityplayer.getCurrentEquippedItem().splitStack(1);
                secInv.setInventorySlotContents(0, newItem);
                InventoryChanged(secInv);
                return true;
            }
        }
        return false;
    }

    private boolean insertIntInv(EntityPlayer entityplayer, SimpleStackInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv == this.inv && !isUpgradeAllowed(i, entityplayer.getCurrentEquippedItem())) {
                continue;
            }
            ItemStack item = inv.getStackInSlot(i);
            if (item == null) {
                inv.setInventorySlotContents(i, entityplayer.getCurrentEquippedItem().splitStack(1));
                InventoryChanged(inv);
                return true;
            } else if (item.getItemDamage() == entityplayer.getCurrentEquippedItem().getItemDamage()) {
                if (item.stackSize < inv.getInventoryStackLimit()) {
                    item.stackSize++;
                    entityplayer.getCurrentEquippedItem().splitStack(1);
                    inv.setInventorySlotContents(i, item);
                    InventoryChanged(inv);
                    return true;
                }
            }
        }
        return false;
    }

    /** Applies the table's permanent-upgrade rules to GUI and held-card insertion. */
    public boolean isUpgradeAllowed(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inv.getSizeInventory()
                || stack == null
                || stack.getItem() != LogisticsPipes.UpgradeItem) {
            return false;
        }
        IPipeUpgrade upgrade = LogisticsPipes.UpgradeItem.getUpgradeForItem(stack, null);
        return upgrade != null && upgrade.isAllowedForPipe(pipe)
                && (!(pipe instanceof RequestTablePipe table) || table.isUpgradeAllowed());
    }

    public UUID getSecurityID() {
        return uuid;
    }

    public void insetSecurityID(UUID id) {
        ItemStack stack = new ItemStack(LogisticsPipes.LogisticsItemCard, 1, LogisticsItemCard.SEC_CARD);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString("UUID", id.toString());
        secInv.setInventorySlotContents(0, stack);
        InventoryChanged(secInv);
    }

    public void securityTick() {
        if ((getSecurityID()) != null) {
            if (!SimpleServiceLocator.securityStationManager.isAuthorized(uuidS)) {
                securityDelay++;
            } else {
                securityDelay = 0;
            }
            if (securityDelay > 20) {
                secInv.clearInventorySlotContents(0);
                InventoryChanged(secInv);
            }
        }
    }

    @Override
    public boolean isAdvancedSatelliteCrafter() {
        return isAdvancedCrafter;
    }

    @Override
    public boolean hasInstantSatelliteUpgrade() {
        return hasInstantSatelliteUpgrade;
    }

    @Override
    public boolean isFuzzyUpgrade() {
        return isFuzzyUpgrade;
    }

    @Override
    public int getFluidCrafter() {
        return liquidCrafter;
    }

    @Override
    public boolean hasByproductExtractor() {
        return hasByproductExtractor;
    }

    /**
     * @return false: a priority upgrade never goes into a pipe's slots
     */
    @Override
    public boolean hasPriorityUpgrade() {
        return false;
    }

    @Override
    public boolean hasPatternUpgrade() {
        return hasPatternUpgrade;
    }

    @Override
    public boolean hasCraftingMonitoringUpgrade() {
        return pipe instanceof RequestTablePipe table ? table.hasMonitoringUpgrade() : hasCraftingMonitoringUpgrade;
    }

    @Override
    public int getCrafterCleanup() {
        return craftingCleanup;
    }

    public boolean hasLogicControll() {
        return hasLogicControll;
    }
}
