package logisticspipes.pipes.upgrades;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.LogisticsPipes;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.upgrades.sneaky.SidedSneakyUpgrade;

/**
 * The faces a sneaky upgrade works through, in order, and the conversion of the old sneaky upgrades: a sided one
 * becomes the sneaky upgrade with its face, the combined one keeps the faces of the sneaky upgrades in its slots. An up
 * sneaky upgrade has the item damage of the sneaky upgrade now, so it only counts as old in an upgrade inventory saved
 * before (no faces saved with it).
 */
public final class SneakyFaces {

    public static final ForgeDirection[] NONE = {};

    private SneakyFaces() {}

    /**
     * @return true for the sneaky upgrade
     */
    public static boolean isSneaky(ItemStack stack) {
        return stack != null && stack.getItem() == LogisticsPipes.UpgradeItem
                && stack.getItemDamage() == ItemUpgrade.SNEAKY;
    }

    /**
     * @return the face of an old sided sneaky upgrade, null for any other stack
     */
    public static ForgeDirection sidedFace(ItemStack stack) {
        if (stack == null || stack.getItem() != LogisticsPipes.UpgradeItem) {
            return null;
        }
        IPipeUpgrade upgrade = LogisticsPipes.UpgradeItem.getUpgradeForItem(stack, null);
        return upgrade instanceof SidedSneakyUpgrade sided ? sided.getSneakyOrientation() : null;
    }

    /**
     * @return true for the old combined sneaky upgrade
     */
    public static boolean isCombined(ItemStack stack) {
        return stack != null && stack.getItem() == LogisticsPipes.UpgradeItem
                && stack.getItemDamage() == ItemUpgrade.SNEAKY_COMBINATION;
    }

    /**
     * @return a new sneaky upgrade
     */
    public static ItemStack newSneaky() {
        return new ItemStack(LogisticsPipes.UpgradeItem, 1, ItemUpgrade.SNEAKY);
    }

    public static ForgeDirection[] read(NBTTagCompound nbt, String key) {
        int[] ordinals = nbt.getIntArray(key);
        List<ForgeDirection> faces = new ArrayList<>(ordinals.length);
        for (int ordinal : ordinals) {
            ForgeDirection face = ForgeDirection.getOrientation(ordinal);
            if (face != ForgeDirection.UNKNOWN && !faces.contains(face)) {
                faces.add(face);
            }
        }
        return faces.toArray(NONE);
    }

    public static void write(NBTTagCompound nbt, String key, ForgeDirection[] faces) {
        int[] ordinals = new int[faces.length];
        for (int i = 0; i < faces.length; i++) {
            ordinals[i] = faces[i].ordinal();
        }
        nbt.setIntArray(key, ordinals);
    }

    /**
     * @return the faces with that face added at the end, or taken out if it was there
     */
    public static ForgeDirection[] toggle(ForgeDirection[] faces, ForgeDirection face) {
        List<ForgeDirection> list = new ArrayList<>(Arrays.asList(faces));
        if (!list.remove(face)) {
            list.add(face);
        }
        return list.toArray(NONE);
    }

    /**
     * @return the faces with that face added at the end if it isn't there
     */
    public static ForgeDirection[] add(ForgeDirection[] faces, ForgeDirection face) {
        for (ForgeDirection has : faces) {
            if (has == face) {
                return faces;
            }
        }
        return toggle(faces, face);
    }

    /**
     * For the settings window: the number of faces in bits 0-2, then 3 bits per face in order.
     */
    public static int encode(ForgeDirection[] faces) {
        int value = faces.length;
        for (int i = 0; i < faces.length; i++) {
            value |= faces[i].ordinal() << (3 + 3 * i);
        }
        return value;
    }

    public static ForgeDirection[] decode(int value) {
        int count = Math.min(value & 7, 6);
        ForgeDirection[] faces = new ForgeDirection[count];
        for (int i = 0; i < count; i++) {
            faces[i] = ForgeDirection.getOrientation((value >> (3 + 3 * i)) & 7);
        }
        return faces;
    }

    /**
     * Converts the old sneaky upgrades of an upgrade inventory saved before the faces were (no faces key): in slot
     * order the first sneaky one decides the faces, an up one or a sided one its face, a combined one the faces of the
     * sneaky upgrades in its own slots; then every old one becomes a sneaky upgrade.
     *
     * @param combinedSlots the sneaky slots of a combined upgrade, null for a module
     * @return the faces
     */
    public static ForgeDirection[] convertLegacy(IInventory inv, IInventory combinedSlots) {
        ForgeDirection[] faces = null;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            ForgeDirection face = isSneaky(stack) ? ForgeDirection.UP : sidedFace(stack);
            if (face != null) {
                if (faces == null) {
                    faces = new ForgeDirection[] { face };
                }
                inv.setInventorySlotContents(i, newSneaky());
            } else if (isCombined(stack)) {
                if (faces == null) {
                    faces = NONE;
                    if (combinedSlots != null) {
                        for (int j = 0; j < combinedSlots.getSizeInventory(); j++) {
                            ItemStack inSlot = combinedSlots.getStackInSlot(j);
                            ForgeDirection slotFace = isSneaky(inSlot) ? ForgeDirection.UP : sidedFace(inSlot);
                            if (slotFace != null) {
                                faces = add(faces, slotFace);
                            }
                        }
                    }
                }
                inv.setInventorySlotContents(i, newSneaky());
            }
        }
        return faces == null ? NONE : faces;
    }
}
