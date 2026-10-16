package logisticspipes.gui.modularUI.pipes.patterncrafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentTranslation;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import logisticspipes.crafting.PatternSatelliteInfo;
import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternRecipeImport;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;

/**
 * Carries the non-slot interactions of a pattern editor: selecting a pattern, pattern actions, satellite targets and
 * NEI imports, applied on the server to the pattern selected in the shared {@link PatternEditorState}. The server
 * pushes the known satellites.
 * <p>
 * Works for any {@link logisticspipes.crafting.pattern.PatternSource}; {@link PatternCraftingSyncHandler} adds the
 * pattern crafting pipe's state and commands.
 */
public class PatternEditorSyncHandler extends SyncHandler<PatternEditorSyncHandler> {

    private static final int C_SELECT = 0;
    private static final int C_PATTERN_ACTION = 1;
    private static final int C_SATELLITE = 2;
    private static final int C_IMPORT = 3;
    private static final int C_REFRESH_SATELLITES = 4;
    private static final int C_MAIN_OUTPUT = 5;

    private static final int S_SATELLITES = 100;
    private static final int S_SELECT = 101;

    public enum PatternAction {
        CLEAR,
        MULTIPLY,
        TOGGLE_TYPE,
        TOGGLE_ORE_DICT,
        TOGGLE_IGNORE_NBT
    }

    protected final PatternEditorState state;

    private List<PatternSatelliteInfo> satellites = Collections.emptyList();
    private int satelliteRevision;

    public PatternEditorSyncHandler(PatternEditorState state) {
        this.state = state;
        allowC2S();
    }

    public PatternEditorState getState() {
        return state;
    }

    // region client accessors

    public List<PatternSatelliteInfo> getSatellites() {
        return satellites;
    }

    /**
     * Increases every time a new satellite list arrives, so open lists know when to rebuild.
     */
    public int getSatelliteRevision() {
        return satelliteRevision;
    }

    public PatternSatelliteInfo findSatellite(int satelliteId, String satelliteUuid, boolean fluid) {
        PatternSatelliteInfo.SatelliteType type = fluid ? PatternSatelliteInfo.SatelliteType.FLUID
                : PatternSatelliteInfo.SatelliteType.ITEM;
        for (PatternSatelliteInfo satellite : satellites) {
            if (satellite.type() != type) {
                continue;
            }
            if (satelliteUuid != null && !satelliteUuid.isEmpty() ? satelliteUuid.equals(satellite.uuid())
                    : satellite.id() == satelliteId) {
                return satellite;
            }
        }
        return null;
    }

    // endregion

    // region client actions

    public void select(int slot) {
        state.select(slot);
        syncToServer(C_SELECT, buf -> buf.writeVarIntToBuffer(slot));
    }

    public void patternAction(PatternAction action) {
        syncToServer(C_PATTERN_ACTION, buf -> buf.writeVarIntToBuffer(action.ordinal()));
    }

    public void assignSatellite(int inputSlot, int satelliteId, String satelliteUuid, boolean fluid) {
        syncToServer(C_SATELLITE, buf -> {
            buf.writeVarIntToBuffer(inputSlot);
            buf.writeVarIntToBuffer(satelliteId);
            NetworkUtils.writeStringSafe(buf, satelliteUuid == null ? "" : satelliteUuid);
            buf.writeBoolean(fluid);
        });
    }

    public void importRecipe(PatternRecipeImport recipe) {
        syncToServer(C_IMPORT, buf -> buf.writeNBTTagCompoundToBuffer(recipe.writeToNBT()));
    }

    public void selectMainOutput(int outputSlot) {
        syncToServer(C_MAIN_OUTPUT, buf -> buf.writeVarIntToBuffer(outputSlot));
    }

    public void refreshSatellites() {
        syncToServer(C_REFRESH_SATELLITES);
    }

    // endregion

    @Override
    public void detectAndSendChanges(boolean init) {
        if (init) {
            sendSatellites();
            syncToClient(S_SELECT, buf -> buf.writeVarIntToBuffer(state.getSelectedSlot()));
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        switch (id) {
            case S_SATELLITES -> {
                satellites = new LPDataInputStream(buf).readList(PatternSatelliteInfo::readData);
                satelliteRevision++;
            }
            case S_SELECT -> state.select(buf.readVarIntFromBuffer());
            default -> {}
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        switch (id) {
            case C_SELECT -> state.select(buf.readVarIntFromBuffer());
            case C_PATTERN_ACTION -> {
                int action = buf.readVarIntFromBuffer();
                if (action >= 0 && action < PatternAction.values().length) {
                    applyPatternAction(PatternAction.values()[action]);
                }
            }
            case C_SATELLITE -> applySatellite(
                    buf.readVarIntFromBuffer(),
                    buf.readVarIntFromBuffer(),
                    NetworkUtils.readStringSafe(buf),
                    buf.readBoolean());
            case C_IMPORT -> applyImport(PatternRecipeImport.readFromNBT(buf.readNBTTagCompoundFromBuffer()));
            case C_REFRESH_SATELLITES -> sendSatellites();
            case C_MAIN_OUTPUT -> {
                int outputSlot = buf.readVarIntFromBuffer();
                if (state.hasPattern()) {
                    state.getPattern().setMainOutputSlot(outputSlot);
                    state.markChanged();
                }
            }
            default -> {}
        }
    }

    private void sendSatellites() {
        EntityPlayer player = getSyncManager().getPlayer();
        List<PatternSatelliteInfo> known = new ArrayList<>(
                PipeItemsPatternSatelliteLogistics.getKnownSatellitesFor(player));
        known.addAll(PipeFluidPatternSatelliteLogistics.getKnownSatellitesFor(player));
        syncToClient(
                S_SATELLITES,
                buf -> new LPDataOutputStream(buf)
                        .writeList(known, (stream, satellite) -> satellite.writeData(stream)));
    }

    // region server logic

    private void applyPatternAction(PatternAction action) {
        ItemStack stack = state.getPatternStack();
        if (!PatternEditorState.isPattern(stack)) {
            return;
        }
        AbstractPattern pattern = ItemPattern.fromStack(stack);
        switch (action) {
            case CLEAR -> pattern.clear();
            case MULTIPLY -> {
                if (!canMultiply(pattern)) {
                    getSyncManager().getPlayer().addChatComponentMessage(
                            new ChatComponentTranslation("gui.patterncrafting.multiply.toolarge"));
                    return;
                }
                pattern.multiply(2);
            }
            case TOGGLE_TYPE -> togglePatternType(stack);
            case TOGGLE_ORE_DICT -> pattern.toggleOreDictSubstitution();
            case TOGGLE_IGNORE_NBT -> pattern.toggleIgnoreNbt();
        }
        state.markChanged();
    }

    private static boolean canMultiply(AbstractPattern pattern) {
        for (int slot = 0; slot < pattern.getItemSlotCount(); slot++) {
            IPatternStack stack = pattern.getPatternStackInSlot(slot);
            if (stack instanceof PatternItemStack && stack.getAmount() * 2 > PatternRecipeImport.MAX_ITEM_AMOUNT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Switches between crafting and processing while keeping the recipe when it fits the new type.
     * <p>
     * Slot meanings differ between the two types, so entries (and their satellite targets) are re-packed from the first
     * slot on. A processing pattern that is too large for the 3x3 crafting grid is cleared instead.
     */
    private static void togglePatternType(ItemStack stack) {
        AbstractPattern old = ItemPattern.fromStack(stack);
        List<IPatternStack> inputs = new ArrayList<>();
        List<int[]> satelliteIds = new ArrayList<>();
        List<String[]> satelliteUuids = new ArrayList<>();
        for (int slot = 0; slot < old.getIngredientSlotCount(); slot++) {
            IPatternStack input = old.getPatternStackInSlot(slot);
            if (input == null) {
                continue;
            }
            inputs.add(input);
            satelliteIds
                    .add(new int[] { old.getSatelliteIdForInputSlot(slot), old.getFluidSatelliteIdForInputSlot(slot) });
            satelliteUuids.add(
                    new String[] { old.getSatelliteUuidForInputSlot(slot),
                            old.getFluidSatelliteUuidForInputSlot(slot) });
        }
        List<IPatternStack> outputs = new ArrayList<>();
        List<int[]> byproductIds = new ArrayList<>();
        List<String[]> byproductUuids = new ArrayList<>();
        int mainOutput = -1;
        for (int slot = 0; slot < old.getResultSlotCount(); slot++) {
            IPatternStack output = old.getPatternStackInSlot(old.getResultSlotStart() + slot);
            if (output == null) continue;
            if (slot == old.getMainOutputSlot()) mainOutput = outputs.size();
            outputs.add(output);
            byproductIds.add(
                    new int[] { old.getByproductSatelliteIdForOutputSlot(slot),
                            old.getFluidByproductSatelliteIdForOutputSlot(slot) });
            byproductUuids.add(
                    new String[] { old.getByproductSatelliteUuidForOutputSlot(slot),
                            old.getFluidByproductSatelliteUuidForOutputSlot(slot) });
        }
        boolean toProcessing = !ItemPattern.isProcessingPattern(stack);
        ItemPattern.setProcessingPattern(stack, toProcessing);
        AbstractPattern next = ItemPattern.fromStack(stack);
        if (inputs.size() > next.getIngredientSlotCount() || outputs.size() > next.getResultSlotCount()) {
            return;
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            next.setPatternStackInSlot(slot, inputs.get(slot));
            next.setSatelliteTargetForInputSlot(slot, satelliteIds.get(slot)[0], satelliteUuids.get(slot)[0]);
            next.setFluidSatelliteTargetForInputSlot(slot, satelliteIds.get(slot)[1], satelliteUuids.get(slot)[1]);
        }
        for (int i = 0; i < outputs.size(); i++) {
            next.setPatternStackInSlot(next.getResultSlotStart() + i, outputs.get(i));
            next.setByproductSatelliteTargetForOutputSlot(i, byproductIds.get(i)[0], byproductUuids.get(i)[0]);
            next.setFluidByproductSatelliteTargetForOutputSlot(i, byproductIds.get(i)[1], byproductUuids.get(i)[1]);
        }
        next.setMainOutputSlot(mainOutput);
    }

    /**
     * Stores the satellite target on the pattern input slot and tells the source, which may link the satellite.
     */
    private void applySatellite(int inputSlot, int satelliteId, String satelliteUuid, boolean fluid) {
        ItemStack stack = state.getPatternStack();
        if (!PatternEditorState.isPattern(stack)) {
            return;
        }
        AbstractPattern pattern = ItemPattern.fromStack(stack);
        if (inputSlot < 0 || inputSlot >= pattern.getItemSlotCount()) {
            return;
        }
        if (inputSlot >= pattern.getResultSlotStart()) {
            int outputSlot = inputSlot - pattern.getResultSlotStart();
            if (fluid) {
                pattern.setFluidByproductSatelliteTargetForOutputSlot(outputSlot, satelliteId, satelliteUuid);
            } else {
                pattern.setByproductSatelliteTargetForOutputSlot(outputSlot, satelliteId, satelliteUuid);
            }
        } else if (fluid) {
            pattern.setFluidSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
        } else {
            pattern.setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
        }
        state.getSource().onSatelliteAssigned(satelliteId, satelliteUuid, fluid);
        state.markChanged();
    }

    /**
     * Writes an NEI recipe into the selected pattern. If the selected slot is empty, the first blank pattern is used
     * instead, so configured patterns are never overwritten without being selected.
     */
    private void applyImport(PatternRecipeImport recipe) {
        if (recipe.isEmpty()) {
            return;
        }
        if (!state.hasPattern()) {
            int blankSlot = findBlankPatternSlot();
            if (blankSlot < 0) {
                getSyncManager().getPlayer()
                        .addChatComponentMessage(new ChatComponentTranslation("gui.patterncrafting.import.nopattern"));
                return;
            }
            state.select(blankSlot);
            syncToClient(S_SELECT, buf -> buf.writeVarIntToBuffer(blankSlot));
        }
        recipe.applyTo(state.getPatternStack());
        state.markChanged();
    }

    private int findBlankPatternSlot() {
        for (int slot = 0; slot < state.getPatternCount(); slot++) {
            ItemStack stack = state.getPatternStack(slot);
            if (PatternEditorState.isPattern(stack) && !ItemPattern.fromStack(stack).isConfigured()) {
                return slot;
            }
        }
        return -1;
    }

    // endregion
}
