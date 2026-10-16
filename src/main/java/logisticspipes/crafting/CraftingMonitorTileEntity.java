package logisticspipes.crafting;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.blocks.LogisticsSolidTileEntity;
import logisticspipes.interfaces.IGuiTileEntity;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.WorldUtil;

/** Solid-block access point for inspecting and cancelling live pattern-crafting requests. */
public class CraftingMonitorTileEntity extends LogisticsSolidTileEntity implements IGuiTileEntity {

    private CoreRoutedPipe cachedConnectedPipe;

    @Override
    public void notifyOfBlockChange() {
        cachedConnectedPipe = null;
    }

    @Override
    public CoordinatesGuiProvider getGuiProvider() {
        return NewGuiHandler.getGui(CraftingMonitorGuiProvider.class).setEntries(getMonitorEntries());
    }

    public List<PatternCraftingMonitorEntry> getMonitorEntries() {
        CoreRoutedPipe pipe = getConnectedPipe();
        if (pipe == null) {
            return Collections.emptyList();
        }
        return PatternCraftingMonitorRegistry.buildAll(pipe.getRouter());
    }

    public boolean cancel(UUID instanceId) {
        CoreRoutedPipe pipe = getConnectedPipe();
        return pipe != null && PatternCraftingMonitorRegistry.cancelInstance(instanceId, pipe.getRouter());
    }

    public CoreRoutedPipe getConnectedPipe() {
        if (cachedConnectedPipe == null) {
            WorldUtil world = new WorldUtil(this);
            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                TileEntity tile = world.getAdjacentTileEntitie(direction);
                if (tile instanceof LogisticsTileGenericPipe
                        && ((LogisticsTileGenericPipe) tile).pipe instanceof CoreRoutedPipe) {
                    cachedConnectedPipe = (CoreRoutedPipe) ((LogisticsTileGenericPipe) tile).pipe;
                    break;
                }
            }
        }
        return cachedConnectedPipe;
    }
}
