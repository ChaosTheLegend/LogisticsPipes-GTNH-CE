package logisticspipes.crafting;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Stable identity for one object owned by a pattern-crafting instance.
 *
 * <p>
 * The instance id groups the complete recursive request. The object id distinguishes orders, deliveries, buffered
 * ownership records and satellite batches inside that instance.
 * </p>
 */
public final class PatternCraftingReference {

    private static final String INSTANCE_SUFFIX = "InstanceId";
    private static final String OBJECT_SUFFIX = "ObjectId";

    private final UUID instanceId;
    private final UUID objectId;

    private PatternCraftingReference(UUID instanceId, UUID objectId) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.objectId = Objects.requireNonNull(objectId, "objectId");
    }

    public static PatternCraftingReference createInstance() {
        UUID instanceId = UUID.randomUUID();
        return new PatternCraftingReference(instanceId, UUID.randomUUID());
    }

    public static PatternCraftingReference createObject(UUID instanceId) {
        return new PatternCraftingReference(instanceId, UUID.randomUUID());
    }

    public static PatternCraftingReference readFromNBT(NBTTagCompound tag, String prefix) {
        String instance = tag.getString(prefix + INSTANCE_SUFFIX);
        String object = tag.getString(prefix + OBJECT_SUFFIX);
        if (instance.isEmpty() || object.isEmpty()) {
            return null;
        }
        try {
            return new PatternCraftingReference(UUID.fromString(instance), UUID.fromString(object));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public PatternCraftingReference createChild() {
        return createObject(instanceId);
    }

    public UUID instanceId() {
        return instanceId;
    }

    public UUID objectId() {
        return objectId;
    }

    public boolean belongsTo(PatternCraftingReference other) {
        return other != null && instanceId.equals(other.instanceId);
    }

    public void writeToNBT(NBTTagCompound tag, String prefix) {
        tag.setString(prefix + INSTANCE_SUFFIX, instanceId.toString());
        tag.setString(prefix + OBJECT_SUFFIX, objectId.toString());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PatternCraftingReference other)) {
            return false;
        }
        return instanceId.equals(other.instanceId) && objectId.equals(other.objectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, objectId);
    }

    @Override
    public String toString() {
        return instanceId.toString().substring(0, 8) + "/" + objectId.toString().substring(0, 8);
    }
}
