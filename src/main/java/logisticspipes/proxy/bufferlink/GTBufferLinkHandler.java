package logisticspipes.proxy.bufferlink;

import java.lang.reflect.Field;

import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicHull;
import gregtech.common.tileentities.machines.long_distance.MTELongDistancePipelineBase;
import gregtech.common.tileentities.machines.long_distance.MTELongDistancePipelineFluid;
import gregtech.common.tileentities.machines.long_distance.MTELongDistancePipelineItem;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * GregTech buffers:
 * <ul>
 * <li>Machine hulls (any tier, also bronze/steel): the hull itself is the buffer, so both connectors must touch the
 * same hull.</li>
 * <li>Long distance pipelines: items go in at the input end and come out at the output end, never back. The key is the
 * output end. The item pipeline carries only items, the fluid pipeline only fluid packets.</li>
 * </ul>
 */
public class GTBufferLinkHandler implements IBufferLinkHandler {

    private static final Field TARGET = findTargetField();

    private static Field findTargetField() {
        try {
            Field field = MTELongDistancePipelineBase.class.getDeclaredField("mTarget");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean isType(TileEntity tile) {
        return tile instanceof IGregTechTileEntity gtTile && gtTile.getMetaTileEntity() instanceof MTEBasicHull;
    }

    @Override
    public Object getSendKey(TileEntity tile, ItemIdentifierStack stack) {
        IMetaTileEntity mte = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (!(mte instanceof MTELongDistancePipelineBase pipeline)) {
            return tile;
        }
        // false when the pipeline is broken, too short, disabled or its output end isn't loaded
        if (!pipeline.checkTarget()) {
            return null;
        }
        MTELongDistancePipelineBase target = getTarget(pipeline);
        return target == null ? null : target.getBaseMetaTileEntity();
    }

    @Override
    public BufferContent getContent(TileEntity tile) {
        IMetaTileEntity mte = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (mte instanceof MTELongDistancePipelineFluid) {
            return BufferContent.FLUIDS;
        }
        if (mte instanceof MTELongDistancePipelineItem) {
            return BufferContent.ITEMS;
        }
        // a hull has an item slot and a tank; an unknown kind of pipeline carries nothing we know of
        return mte instanceof MTELongDistancePipelineBase ? BufferContent.ITEMS : BufferContent.BOTH;
    }

    @Override
    public Object getReceiveKey(TileEntity tile) {
        IMetaTileEntity mte = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (mte instanceof MTELongDistancePipelineBase pipeline && getTarget(pipeline) != null) {
            return null; // an input end: nothing comes out here
        }
        return tile;
    }

    private static MTELongDistancePipelineBase getTarget(MTELongDistancePipelineBase pipeline) {
        if (TARGET == null) {
            return null;
        }
        try {
            return (MTELongDistancePipelineBase) TARGET.get(pipeline);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
