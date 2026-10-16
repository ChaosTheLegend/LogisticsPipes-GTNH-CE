package logisticspipes.crafting;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Identifies a recipe output, its producing crafting order and its optional extraction satellite.
 */
public final class PatternByproductTarget {

    private static final String PATTERN_SLOT_SUFFIX = "PatternSlot";
    private static final String SOURCE_PREFIX_SUFFIX = "Source";
    private static final String OUTPUT_SLOT_SUFFIX = "OutputSlot";
    private static final String SATELLITE_ID_SUFFIX = "SatelliteId";
    private static final String SATELLITE_UUID_SUFFIX = "SatelliteUuid";
    private static final String FLUID_SUFFIX = "Fluid";

    private final int patternSlot;
    private final PatternCraftingReference sourceReference;
    private final int outputSlot;
    private final int satelliteId;
    private final String satelliteUuid;
    private final boolean fluid;

    public PatternByproductTarget(int outputSlot, int satelliteId, String satelliteUuid, boolean fluid) {
        this(-1, outputSlot, satelliteId, satelliteUuid, fluid, null);
    }

    public PatternByproductTarget(int patternSlot, int outputSlot, int satelliteId, String satelliteUuid, boolean fluid,
            PatternCraftingReference sourceReference) {
        this.patternSlot = patternSlot;
        this.sourceReference = sourceReference;
        this.outputSlot = Math.max(0, outputSlot);
        this.satelliteId = Math.max(0, satelliteId);
        this.satelliteUuid = satelliteUuid == null ? "" : satelliteUuid;
        this.fluid = fluid;
    }

    static PatternByproductTarget readFromNBT(NBTTagCompound tag, String prefix) {
        int satelliteId = tag.getInteger(prefix + SATELLITE_ID_SUFFIX);
        String satelliteUuid = tag.getString(prefix + SATELLITE_UUID_SUFFIX);
        if (!tag.hasKey(prefix + OUTPUT_SLOT_SUFFIX)) {
            return null;
        }
        return new PatternByproductTarget(
                tag.hasKey(prefix + PATTERN_SLOT_SUFFIX) ? tag.getInteger(prefix + PATTERN_SLOT_SUFFIX) : -1,
                tag.getInteger(prefix + OUTPUT_SLOT_SUFFIX),
                satelliteId,
                satelliteUuid,
                tag.getBoolean(prefix + FLUID_SUFFIX),
                PatternCraftingReference.readFromNBT(tag, prefix + SOURCE_PREFIX_SUFFIX));
    }

    public int getPatternSlot() {
        return patternSlot;
    }

    public PatternCraftingReference getSourceReference() {
        return sourceReference;
    }

    public PatternByproductTarget withSourceReference(PatternCraftingReference reference) {
        return new PatternByproductTarget(patternSlot, outputSlot, satelliteId, satelliteUuid, fluid, reference);
    }

    public int getOutputSlot() {
        return outputSlot;
    }

    public int getSatelliteId() {
        return satelliteId;
    }

    public String getSatelliteUuid() {
        return satelliteUuid;
    }

    public boolean isFluid() {
        return fluid;
    }

    public boolean isConfigured() {
        return satelliteId > 0 || !satelliteUuid.isEmpty();
    }

    void writeToNBT(NBTTagCompound tag, String prefix) {
        tag.setInteger(prefix + PATTERN_SLOT_SUFFIX, patternSlot);
        if (sourceReference != null) {
            sourceReference.writeToNBT(tag, prefix + SOURCE_PREFIX_SUFFIX);
        }
        tag.setInteger(prefix + OUTPUT_SLOT_SUFFIX, outputSlot);
        tag.setInteger(prefix + SATELLITE_ID_SUFFIX, satelliteId);
        tag.setString(prefix + SATELLITE_UUID_SUFFIX, satelliteUuid);
        tag.setBoolean(prefix + FLUID_SUFFIX, fluid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PatternByproductTarget other)) {
            return false;
        }
        return patternSlot == other.patternSlot && Objects.equals(sourceReference, other.sourceReference)
                && outputSlot == other.outputSlot
                && satelliteId == other.satelliteId
                && fluid == other.fluid
                && satelliteUuid.equals(other.satelliteUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patternSlot, sourceReference, outputSlot, satelliteId, satelliteUuid, fluid);
    }
}
