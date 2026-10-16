package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PatternCraftingHudState {

    private final PipeItemsPatternCraftingLogistics.BlockingMode blockingMode;
    private final List<PatternInfo> patterns;

    public PatternCraftingHudState() {
        this(PipeItemsPatternCraftingLogistics.BlockingMode.OFF, new ArrayList<>());
    }

    public PatternCraftingHudState(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode) {
        this(blockingMode, new ArrayList<>());
    }

    public PatternCraftingHudState(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode,
            List<PatternInfo> patterns) {
        this.blockingMode = blockingMode == null ? PipeItemsPatternCraftingLogistics.BlockingMode.OFF : blockingMode;
        this.patterns = patterns == null ? new ArrayList<>() : patterns;
    }

    public static PatternCraftingHudState empty() {
        return new PatternCraftingHudState();
    }

    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeEnum(blockingMode);
        data.writeList(patterns, (stream, pattern) -> pattern.writeData(stream));
    }

    public static PatternCraftingHudState readData(LPDataInputStream data) throws IOException {
        PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = data
                .readEnum(PipeItemsPatternCraftingLogistics.BlockingMode.class);
        return new PatternCraftingHudState(blockingMode, data.readList(PatternInfo::readData));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PatternCraftingHudState that)) {
            return false;
        }
        return blockingMode == that.blockingMode && patterns.equals(that.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockingMode, patterns);
    }

    @Getter
    public static class PatternInfo {

        private final int slot;
        private final List<IngredientInfo> ingredients;
        private final List<OutputInfo> outputs;
        private String status;
        @Setter
        private boolean active;

        public PatternInfo(int slot) {
            this(slot, new ArrayList<>(), new ArrayList<>(), "", false);
        }

        private PatternInfo(int slot, List<IngredientInfo> ingredients, List<OutputInfo> outputs, String status,
                boolean active) {
            this.slot = slot;
            this.ingredients = ingredients == null ? new ArrayList<>() : ingredients;
            this.outputs = outputs == null ? new ArrayList<>() : outputs;
            this.status = status == null ? "" : status;
            this.active = active;
        }

        public void setStatus(String status) {
            this.status = status == null ? "" : status;
        }

        private void writeData(LPDataOutputStream data) throws IOException {
            data.writeInt(slot);
            data.writeList(ingredients, (stream, ingredient) -> ingredient.writeData(stream));
            data.writeList(outputs, (stream, output) -> output.writeData(stream));
            data.writeUTF(status);
            data.writeBoolean(active);
        }

        private static PatternInfo readData(LPDataInputStream data) throws IOException {
            return new PatternInfo(
                    data.readInt(),
                    data.readList(IngredientInfo::readData),
                    data.readList(OutputInfo::readData),
                    data.readUTF(),
                    data.readBoolean());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PatternInfo that)) {
                return false;
            }
            return slot == that.slot && active == that.active
                    && ingredients.equals(that.ingredients)
                    && outputs.equals(that.outputs)
                    && status.equals(that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slot, ingredients, outputs, status, active);
        }
    }

    @Desugar
    public record OutputInfo(ItemIdentifierStack stack, int requestedAmount, int slot) {

        public OutputInfo(ItemIdentifierStack stack, int requestedAmount, int slot) {
            this.stack = stack;
            this.requestedAmount = Math.max(0, requestedAmount);
            this.slot = slot;
        }

        private void writeData(LPDataOutputStream data) throws IOException {
            data.writeItemIdentifierStack(stack);
            data.writeInt(requestedAmount);
            data.writeInt(slot);
        }

        private static OutputInfo readData(LPDataInputStream data) throws IOException {
            return new OutputInfo(data.readItemIdentifierStack(), data.readInt(), data.readInt());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OutputInfo that)) {
                return false;
            }
            return requestedAmount == that.requestedAmount && slot == that.slot && stack.equals(that.stack);
        }

    }

    @Desugar
    public record IngredientInfo(ItemIdentifierStack stack, int bufferedAmount, int slot) {

        public IngredientInfo(ItemIdentifierStack stack, int bufferedAmount, int slot) {
            this.stack = stack;
            this.bufferedAmount = Math.max(0, bufferedAmount);
            this.slot = slot;
        }

        private void writeData(LPDataOutputStream data) throws IOException {
            data.writeItemIdentifierStack(stack);
            data.writeInt(bufferedAmount);
            data.writeInt(slot);
        }

        private static IngredientInfo readData(LPDataInputStream data) throws IOException {
            return new IngredientInfo(data.readItemIdentifierStack(), data.readInt(), data.readInt());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IngredientInfo that)) {
                return false;
            }
            return bufferedAmount == that.bufferedAmount && slot == that.slot && stack.equals(that.stack);
        }

    }
}
