package logisticspipes.crafting.v3.exec;

import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.crafting.v3.model.MatchRule;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/** Saves and loads resource keys. The Minecraft codec lives in {@code crafting.v3.mc}; tests use a string codec. */
public interface KeyCodec {

    void write(ResourceKey key, NBTTagCompound tag);

    /** Null when the item or fluid no longer exists (mod removed). */
    ResourceKey read(NBTTagCompound tag);

    default NBTTagCompound writeKey(ResourceKey key) {
        NBTTagCompound tag = new NBTTagCompound();
        write(key, tag);
        return tag;
    }

    default NBTTagCompound writeMatcher(ResourceMatcher m) {
        NBTTagCompound tag = writeKey(m.key());
        tag.setByte("rule", (byte) m.rule().ordinal());
        if (m.oreName() != null) tag.setString("ore", m.oreName());
        return tag;
    }

    default ResourceMatcher readMatcher(NBTTagCompound tag) {
        ResourceKey key = read(tag);
        if (key == null) return null;
        MatchRule rule = MatchRule.values()[Math.min(tag.getByte("rule"), MatchRule.values().length - 1)];
        return ResourceMatcher.of(key, rule, tag.hasKey("ore") ? tag.getString("ore") : null);
    }
}
