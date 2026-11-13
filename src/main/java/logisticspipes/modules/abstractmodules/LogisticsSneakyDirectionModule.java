package logisticspipes.modules.abstractmodules;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.pipes.upgrades.SneakyFaces;
import logisticspipes.proxy.MainProxy;

/**
 * A module that works on the inventory through the faces of a sneaky upgrade (extractor, provider). Before, these
 * modules had a side setting of their own; a module saved with one gets a sneaky upgrade with that face once it runs
 * (decided 2026-11-09: the side is a sneaky upgrade setting now).
 */
public abstract class LogisticsSneakyDirectionModule extends LogisticsGuiModule {

    // the side saved by an older version, until it became a sneaky upgrade
    private ForgeDirection legacySide = ForgeDirection.UNKNOWN;

    /**
     * @return the faces to work through in order, empty for the face facing the pipe
     */
    public ForgeDirection[] getSneakyFaces() {
        ISlotUpgradeManager upgrades = _service == null ? getModuleUpgrades()
                : _service.getUpgradeManager(slot, positionInt);
        return upgrades.getSneakyOrientations();
    }

    /**
     * @return the first face to work through, UNKNOWN for the face facing the pipe
     */
    public ForgeDirection getSneakyDirection() {
        ForgeDirection[] faces = getSneakyFaces();
        return faces.length == 0 ? ForgeDirection.UNKNOWN : faces[0];
    }

    /**
     * Sets the one face of the module's sneaky upgrade (old windows and packets); it works while one is installed.
     */
    public void setSneakyDirection(ForgeDirection face) {
        getModuleUpgrades().setSneakyFaces(
                face == null || face == ForgeDirection.UNKNOWN ? SneakyFaces.NONE : new ForgeDirection[] { face });
    }

    /**
     * Reads the side an older version saved; call from readFromNBT.
     */
    protected void readLegacySide(NBTTagCompound nbt) {
        if (nbt.hasKey("sneakydirection")) {
            legacySide = ForgeDirection.getOrientation(nbt.getInteger("sneakydirection"));
        } else if (nbt.hasKey("sneakyorientation")) {
            legacySide = switch (nbt.getInteger("sneakyorientation")) {
                case 1 -> ForgeDirection.UP;
                case 2 -> ForgeDirection.SOUTH;
                case 3 -> ForgeDirection.DOWN;
                default -> ForgeDirection.UNKNOWN;
            };
        }
    }

    /**
     * Keeps a side not yet turned into a sneaky upgrade; call from writeToNBT.
     */
    protected void writeLegacySide(NBTTagCompound nbt) {
        if (legacySide != ForgeDirection.UNKNOWN) {
            nbt.setInteger("sneakydirection", legacySide.ordinal());
        }
    }

    /**
     * Turns a side an older version saved into a sneaky upgrade with that face; call from tick. It runs once the module
     * sits in a pipe, so after its upgrades and the speed upgrades an old module gets (ModuleMigration) are in: the
     * sneaky upgrade goes into a free slot, else it replaces one speed upgrade (old Extractor Mk3 and Provider Mk2 fill
     * every slot with speed). Without either the face is still set and works once a sneaky upgrade is put in.
     */
    protected void applyLegacySide() {
        if (legacySide == ForgeDirection.UNKNOWN || _service == null || MainProxy.isClient(_world.getWorld())) {
            return;
        }
        ModuleUpgradeManager upgrades = getModuleUpgrades();
        if (!upgrades.hasSneakyItem()) {
            int slot = sneakySlot(upgrades.getInv());
            if (slot >= 0) {
                upgrades.getInv().setInventorySlotContents(slot, SneakyFaces.newSneaky());
            }
        }
        if (upgrades.getSneakyFaces().length == 0) {
            upgrades.setSneakyFaces(new ForgeDirection[] { legacySide });
        }
        legacySide = ForgeDirection.UNKNOWN;
        upgrades.InventoryChanged(upgrades.getInv());
    }

    /**
     * @return the first free slot, else the first slot with a speed upgrade, else -1
     */
    private static int sneakySlot(IInventory inv) {
        int speedSlot = -1;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null) {
                return i;
            }
            if (speedSlot < 0 && stack.getItem() == LogisticsPipes.UpgradeItem
                    && stack.getItemDamage() == ItemUpgrade.SPEED) {
                speedSlot = i;
            }
        }
        return speedSlot;
    }
}
