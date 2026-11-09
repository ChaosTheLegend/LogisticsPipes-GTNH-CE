package logisticspipes.pipes.upgrades;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.utils.DeprecatedContent;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.item.SimpleStackInventory;
import lombok.Getter;

/**
 * The upgrades of one module: 4 slots. A module owns one (see {@code LogisticsModule#getModuleUpgrades()}) and keeps it
 * when it is taken out of its pipe; a module host also has one per slot for upgrades that were left in an empty slot.
 * <p>
 * While the module sits in a pipe the manager is bound to it: the upgrades installed on the pipe itself count too (old
 * chassis kept module upgrades there), and an upgrade that changes the pipe's connections updates the pipe. Unbound (a
 * module in hand) only the module's own upgrades count.
 */
public class ModuleUpgradeManager implements ISimpleInventoryEventHandler, ISlotUpgradeManager {

    public static final int SLOTS = 4;

    /**
     * Speed upgrades one module takes, in all its slots together.
     */
    public static final int MAX_SPEED_UPGRADES = 4;

    // 16, not 1: old chassis saves hold stacks of fluid crafting and cleanup upgrades in one slot, counted by size
    @Getter
    private final SimpleStackInventory inv = new SimpleStackInventory(SLOTS, "UpgradeInventory", 16);

    private final IPipeUpgrade[] upgrades = new IPipeUpgrade[SLOTS];
    private CoreRoutedPipe pipe;
    private UpgradeManager parent;

    // the faces set in the settings window, in order; they work while a sneaky upgrade is in the slots
    private ForgeDirection[] sneakyFaces = SneakyFaces.NONE;
    private boolean hasSneakyItem = false;
    private boolean isAdvancedCrafter = false;
    private boolean isFuzzyUpgrade = false;
    private int liquidCrafter = 0;
    private boolean hasByproductExtractor = false;
    private boolean hasPatternUpgrade = false;
    private boolean hasPriorityUpgrade = false;
    private int craftingCleanup = 0;
    private int speedUpgradeCount = 0;

    /**
     * An unbound manager, for a module that is in no pipe yet.
     */
    public ModuleUpgradeManager() {
        inv.addListener(this);
    }

    public ModuleUpgradeManager(CoreRoutedPipe pipe, UpgradeManager parent) {
        this();
        bind(pipe, parent);
    }

    /**
     * Binds the manager to the pipe its module sits in; null for both unbinds it.
     */
    public void bind(CoreRoutedPipe pipe, UpgradeManager parent) {
        this.pipe = pipe;
        this.parent = parent;
    }

    /**
     * @return true if no slot holds an upgrade
     */
    public boolean isEmpty() {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves the upgrades into free slots of the other manager. What doesn't fit stays here. A moved sneaky upgrade
     * takes its faces along if the other manager has none set.
     */
    public void moveInto(ModuleUpgradeManager target) {
        boolean moved = false;
        boolean movedSneaky = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            for (int j = 0; j < target.inv.getSizeInventory(); j++) {
                if (target.inv.getStackInSlot(j) == null) {
                    target.inv.setInventorySlotContents(j, stack);
                    inv.setInventorySlotContents(i, null);
                    moved = true;
                    movedSneaky |= SneakyFaces.isSneaky(stack);
                    break;
                }
            }
        }
        if (movedSneaky && target.sneakyFaces.length == 0) {
            target.sneakyFaces = sneakyFaces;
            sneakyFaces = SneakyFaces.NONE;
        }
        if (moved) {
            InventoryChanged(inv);
            target.InventoryChanged(target.inv);
        }
    }

    @Override
    public boolean hasPriorityUpgrade() {
        return hasPriorityUpgrade;
    }

    @Override
    public boolean hasPatternUpgrade() {
        return hasPatternUpgrade || (parent != null && parent.hasPatternUpgrade());
    }

    @Override
    public boolean isAdvancedSatelliteCrafter() {
        return isAdvancedCrafter || (parent != null && parent.isAdvancedSatelliteCrafter());
    }

    @Override
    public boolean hasInstantSatelliteUpgrade() {
        return parent != null && parent.hasInstantSatelliteUpgrade();
    }

    @Override
    public boolean hasByproductExtractor() {
        return hasByproductExtractor || (parent != null && parent.hasByproductExtractor());
    }

    @Override
    public int getFluidCrafter() {
        int fromPipe = parent == null ? 0 : parent.getFluidCrafter();
        return Math.min(liquidCrafter + fromPipe, ItemUpgrade.MAX_LIQUID_CRAFTER);
    }

    @Override
    public boolean isFuzzyUpgrade() {
        return isFuzzyUpgrade || (parent != null && parent.isFuzzyUpgrade());
    }

    @Override
    public int getCrafterCleanup() {
        int fromPipe = parent == null ? 0 : parent.getCrafterCleanup();
        return Math.min(craftingCleanup + fromPipe, ItemUpgrade.MAX_CRAFTING_CLEANUP);
    }

    /**
     * @return the speed upgrades of the module plus the ones in the legacy slots of its pipe (decided 2026-10-30: a
     *         chassis keeps the speed it had and it counts for every module)
     */
    public int getSpeedUpgradeCount() {
        return speedUpgradeCount + (parent == null ? 0 : parent.getSpeedUpgradeCount());
    }

    @Override
    public boolean hasSneakyUpgrade() {
        return getSneakyOrientations().length > 0;
    }

    @Override
    public ForgeDirection getSneakyOrientation() {
        ForgeDirection[] faces = getSneakyOrientations();
        return faces.length == 0 ? ForgeDirection.UNKNOWN : faces[0];
    }

    /**
     * @return the module's own faces, else the ones of a sneaky upgrade on its pipe
     */
    @Override
    public ForgeDirection[] getSneakyOrientations() {
        if (hasOwnSneakyUpgrade()) {
            return sneakyFaces;
        }
        return parent == null ? SneakyFaces.NONE : parent.getSneakyOrientations();
    }

    private boolean hasOwnSneakyUpgrade() {
        return hasSneakyItem && sneakyFaces.length > 0;
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
    public void InventoryChanged(IInventory inventory) {
        boolean needUpdate = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack item = inv.getStackInSlot(i);
            if (item != null) {
                needUpdate |= updateModule(i, upgrades, inv);
            } else if (upgrades[i] != null) {
                needUpdate |= removeUpgrade(i, upgrades);
            }
        }
        // update sneaky direction, speed upgrade count and disconnection
        hasSneakyItem = false;
        isAdvancedCrafter = false;
        isFuzzyUpgrade = false;
        liquidCrafter = 0;
        hasByproductExtractor = false;
        hasPatternUpgrade = false;
        hasPriorityUpgrade = false;
        craftingCleanup = 0;
        speedUpgradeCount = 0;
        for (int i = 0; i < upgrades.length; i++) {
            IPipeUpgrade upgrade = upgrades[i];
            if (upgrade instanceof SneakyUpgrade) {
                hasSneakyItem = true;
            } else if (upgrade instanceof AdvancedSatelliteUpgrade) {
                isAdvancedCrafter = true;
            } else if (upgrade instanceof FuzzyUpgrade) {
                isFuzzyUpgrade = true;
            } else if (upgrade instanceof FluidCraftingUpgrade) {
                liquidCrafter += inv.getStackInSlot(i).stackSize;
            } else if (upgrade instanceof CraftingByproductUpgrade) {
                hasByproductExtractor = true;
            } else if (upgrade instanceof PriorityUpgrade) {
                hasPriorityUpgrade = true;
            } else if (upgrade instanceof PatternUpgrade) {
                hasPatternUpgrade = true;
            } else if (upgrade instanceof CraftingCleanupUpgrade) {
                craftingCleanup += inv.getStackInSlot(i).stackSize;
            } else if (upgrade instanceof SpeedUpgrade) {
                speedUpgradeCount += inv.getStackInSlot(i).stackSize;
            }
        }
        liquidCrafter = Math.min(liquidCrafter, ItemUpgrade.MAX_LIQUID_CRAFTER);
        craftingCleanup = Math.min(craftingCleanup, ItemUpgrade.MAX_CRAFTING_CLEANUP);
        if (needUpdate && pipe != null) {
            pipe.connectionUpdate();
            if (pipe.container != null) {
                pipe.container.sendUpdateToClient();
            }
        }
    }

    public void readFromNBT(NBTTagCompound nbttagcompound, String prefix) {
        inv.readFromNBT(nbttagcompound, "ModuleUpgradeInventory_" + prefix);
        String facesKey = "ModuleSneakyFaces_" + prefix;
        if (nbttagcompound.hasKey(facesKey)) {
            sneakyFaces = SneakyFaces.read(nbttagcompound, facesKey);
        } else {
            // saved before the faces: the old sneaky upgrades give them
            sneakyFaces = SneakyFaces.convertLegacy(inv, null);
        }
        InventoryChanged(inv);
    }

    public void writeToNBT(NBTTagCompound nbttagcompound, String prefix) {
        inv.writeToNBT(nbttagcompound, "ModuleUpgradeInventory_" + prefix);
        SneakyFaces.write(nbttagcompound, "ModuleSneakyFaces_" + prefix, sneakyFaces);
        InventoryChanged(inv);
    }

    /**
     * @return true if the NBT holds upgrades written with that prefix
     */
    public static boolean hasUpgradesInNBT(NBTTagCompound nbttagcompound, String prefix) {
        return nbttagcompound.hasKey("ModuleUpgradeInventory_" + prefix + "items");
    }

    public IItemHandlerModifiable getUpgradeInventory() {
        return new InvWrapper(inv);
    }

    private boolean updateModule(int slot, IPipeUpgrade[] upgrades, IInventory inv) {
        // old sneaky upgrades were converted on load (SneakyFaces.convertLegacy) and can't be put in
        upgrades[slot] = LogisticsPipes.UpgradeItem.getUpgradeForItem(inv.getStackInSlot(slot), upgrades[slot]);
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
}
