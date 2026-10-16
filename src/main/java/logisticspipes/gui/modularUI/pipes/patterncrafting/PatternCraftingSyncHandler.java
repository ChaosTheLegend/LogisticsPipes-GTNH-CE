package logisticspipes.gui.modularUI.pipes.patterncrafting;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics.BlockingMode;
import logisticspipes.utils.AdjacentTile;

/**
 * The pattern editor sync handler of the pattern crafting pipe GUI.
 * <p>
 * On top of the editor interactions, the server pushes the crafting HUD snapshot and the pipe state (blocking mode,
 * crafting target), and the client sends the pipe commands (cancel, return inputs, blocking mode).
 */
public class PatternCraftingSyncHandler extends PatternEditorSyncHandler {

    private static final int C_CANCEL = 20;
    private static final int C_RETURN_INPUTS = 21;
    private static final int C_BLOCKING_MODE = 22;

    private static final int S_HUD = 120;
    private static final int S_STATE = 121;

    private final PipeItemsPatternCraftingLogistics pipe;

    private PatternCraftingHudState hudState = PatternCraftingHudState.empty();
    private BlockingMode blockingMode = BlockingMode.OFF;
    private boolean blockingModeFixed;
    private ItemStack targetStack;
    private ForgeDirection targetSide = ForgeDirection.UNKNOWN;
    private int unsupportedPatterns;

    private PatternCraftingHudState lastSentHud;
    private String lastSentStateKey;

    public PatternCraftingSyncHandler(PatternEditorState state, PipeItemsPatternCraftingLogistics pipe) {
        super(state);
        this.pipe = pipe;
    }

    // region client accessors

    public PatternCraftingHudState getHudState() {
        return hudState;
    }

    public PatternCraftingHudState.PatternInfo getSelectedPatternInfo() {
        for (PatternCraftingHudState.PatternInfo info : hudState.getPatterns()) {
            if (info.getSlot() == state.getSelectedSlot()) {
                return info;
            }
        }
        return null;
    }

    public BlockingMode getBlockingMode() {
        return blockingMode;
    }

    public boolean isBlockingModeFixed() {
        return blockingModeFixed;
    }

    public ItemStack getTargetStack() {
        return targetStack;
    }

    public ForgeDirection getTargetSide() {
        return targetSide;
    }

    /**
     * Whether the pattern in the given slot is hidden from the network because it has fluid entries and the pipe lacks
     * a fluid crafting upgrade.
     */
    public boolean isPatternUnsupported(int patternSlot) {
        return (unsupportedPatterns & (1 << patternSlot)) != 0;
    }

    // endregion

    // region client actions

    public void cancelCraft() {
        syncToServer(C_CANCEL);
    }

    public void returnInputs() {
        syncToServer(C_RETURN_INPUTS);
    }

    public void cycleBlockingMode() {
        if (blockingModeFixed) {
            return;
        }
        BlockingMode[] values = BlockingMode.values();
        BlockingMode next = values[(blockingMode.ordinal() + 1) % values.length];
        blockingMode = next;
        syncToServer(C_BLOCKING_MODE, buf -> buf.writeVarIntToBuffer(next.ordinal()));
    }

    // endregion

    @Override
    public void detectAndSendChanges(boolean init) {
        super.detectAndSendChanges(init);
        PatternCraftingHudState hud = pipe.getPatternModule().getHudState();
        if (init || !hud.equals(lastSentHud)) {
            lastSentHud = hud;
            syncToClient(S_HUD, buf -> hud.writeData(new LPDataOutputStream(buf)));
        }
        BlockingMode mode = pipe.getBlockingMode();
        boolean fixed = pipe.isBlockingModeFixed();
        AdjacentTile target = pipe.getConnectedInventoryTile();
        TileEntity tile = target == null || target.tile == null || target.tile.getWorldObj() == null ? null
                : target.tile;
        ForgeDirection side = target == null ? ForgeDirection.UNKNOWN : target.orientation;
        int unsupported = computeUnsupportedPatterns();
        // the display stack is built on the client (Block.getPickBlock calls the client-only Block.getItem), so only
        // the target's position goes over the wire; block and meta are in the key to resend when the target changes
        String stateKey = mode + "|" + fixed + "|" + side + "|" + unsupported + "|" + describe(tile);
        if (init || !stateKey.equals(lastSentStateKey)) {
            lastSentStateKey = stateKey;
            syncToClient(S_STATE, buf -> {
                buf.writeVarIntToBuffer(mode.ordinal());
                buf.writeBoolean(fixed);
                buf.writeVarIntToBuffer(side.ordinal());
                buf.writeVarIntToBuffer(unsupported);
                buf.writeBoolean(tile != null);
                if (tile != null) {
                    buf.writeInt(tile.xCoord);
                    buf.writeInt(tile.yCoord);
                    buf.writeInt(tile.zCoord);
                }
            });
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        switch (id) {
            case S_HUD -> hudState = PatternCraftingHudState.readData(new LPDataInputStream(buf));
            case S_STATE -> {
                blockingMode = BlockingMode.values()[buf.readVarIntFromBuffer()];
                blockingModeFixed = buf.readBoolean();
                targetSide = ForgeDirection.getOrientation(buf.readVarIntFromBuffer());
                unsupportedPatterns = buf.readVarIntFromBuffer();
                targetStack = buf.readBoolean()
                        ? getDisplayStack(pipe.getWorld(), buf.readInt(), buf.readInt(), buf.readInt())
                        : null;
            }
            default -> super.readOnClient(id, buf);
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        switch (id) {
            case C_CANCEL -> pipe.cancelPatternCraft(state.getSelectedSlot());
            case C_RETURN_INPUTS -> pipe.returnStoredInputsToStorage();
            case C_BLOCKING_MODE -> {
                int mode = buf.readVarIntFromBuffer();
                if (mode >= 0 && mode < BlockingMode.values().length) {
                    pipe.setBlockingMode(BlockingMode.values()[mode]);
                    if (pipe.container != null) {
                        pipe.container.markDirty();
                    }
                }
            }
            default -> super.readOnServer(id, buf);
        }
    }

    private int computeUnsupportedPatterns() {
        int mask = 0;
        for (int slot = 0; slot < PatternEditorState.PATTERN_SLOTS; slot++) {
            ItemStack configured = pipe.getPatternModule().getPatternStack(slot);
            if (configured != null && !pipe.getPatternModule().isPatternCraftingSupported(configured)) {
                mask |= 1 << slot;
            }
        }
        return mask;
    }

    /**
     * Client only: {@link Block#getPickBlock} calls {@code Block.getItem(World, int, int, int)}, which is
     * {@code @SideOnly(CLIENT)} and stripped on a dedicated server ({@code NoSuchMethodError}).
     */
    private static ItemStack getDisplayStack(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        Block block = world.getBlock(x, y, z);
        Item item = Item.getItemFromBlock(block);
        if (item == null) {
            return null;
        }
        try {
            ItemStack pick = block.getPickBlock(null, world, x, y, z);
            if (pick != null && pick.getItem() != null) {
                return pick;
            }
        } catch (RuntimeException ignored) {
            // some blocks require a real ray trace result; fall back to the damage value below
        }
        return new ItemStack(item, 1, block.getDamageValue(world, x, y, z));
    }

    private static String describe(TileEntity tile) {
        if (tile == null) {
            return "";
        }
        World world = tile.getWorldObj();
        return tile.xCoord + ","
                + tile.yCoord
                + ","
                + tile.zCoord
                + ":"
                + Block.getIdFromBlock(world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord))
                + ":"
                + world.getBlockMetadata(tile.xCoord, tile.yCoord, tile.zCoord);
    }
}
