package logisticspipes.crafting.v3.mc;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.utils.item.ItemIdentifier;

/** Stable pattern ids: a 64-bit hash of a signature built from registry names, so they survive restarts. */
public final class PatternIds {

    private PatternIds() {}

    public static String hash(String signature) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < signature.length(); i++) {
            h ^= signature.charAt(i);
            h *= 0x100000001b3L;
        }
        return Long.toHexString(h);
    }

    /** Registry name, damage and NBT of a key; independent of the per-run interning ids. */
    public static String signature(ResourceKey key) {
        if (key instanceof ItemKey) {
            ItemIdentifier id = ((ItemKey) key).id;
            Object name = Item.itemRegistry.getNameForObject(id.item);
            return "i:" + name + ":" + id.itemDamage + ":" + nbt(id.tag);
        }
        if (key instanceof FluidKey) {
            return "f:" + ((FluidKey) key).id.name + ":" + nbt(((FluidKey) key).id.tag);
        }
        return String.valueOf(key);
    }

    private static String nbt(NBTTagCompound tag) {
        return tag == null ? "-" : Integer.toHexString(tag.toString().hashCode());
    }
}
