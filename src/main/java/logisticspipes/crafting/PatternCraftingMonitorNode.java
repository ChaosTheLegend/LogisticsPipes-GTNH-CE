package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

public class PatternCraftingMonitorNode {

    @Getter
    private final ItemIdentifierStack stack;
    @Getter
    private final int unrequestedAmount;
    @Getter
    private final int orderedAmount;
    @Getter
    private boolean inProgress;
    private final List<PatternCraftingMonitorNode> children = new ArrayList<>();

    public PatternCraftingMonitorNode(ItemIdentifierStack stack, int unrequestedAmount, int orderedAmount,
            boolean inProgress) {
        this.stack = stack;
        this.unrequestedAmount = Math.max(0, unrequestedAmount);
        this.orderedAmount = Math.max(0, orderedAmount);
        this.inProgress = inProgress;
    }

    public List<PatternCraftingMonitorNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(PatternCraftingMonitorNode child) {
        if (child != null && child.hasVisibleWork()) {
            children.add(child);
            inProgress |= child.isInProgress();
        }
    }

    public void addChildren(List<PatternCraftingMonitorNode> nodes) {
        for (PatternCraftingMonitorNode node : nodes) {
            addChild(node);
        }
    }

    public boolean hasVisibleWork() {
        return (stack != null && stack.getStackSize() > 0) || !children.isEmpty() || inProgress;
    }

    public int getTreeRootSize() {
        int subSize = 0;
        for (PatternCraftingMonitorNode child : children) {
            subSize += child.getTreeRootSize();
        }
        return Math.max(1, subSize);
    }

    public int getDepth() {
        int depth = 1;
        for (PatternCraftingMonitorNode child : children) {
            depth = Math.max(depth, 1 + child.getDepth());
        }
        return depth;
    }

    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeItemIdentifierStack(stack);
        data.writeInt(unrequestedAmount);
        data.writeInt(orderedAmount);
        data.writeBoolean(inProgress);
        data.writeInt(children.size());
        for (PatternCraftingMonitorNode child : children) {
            child.writeData(data);
        }
    }

    public static PatternCraftingMonitorNode readData(LPDataInputStream data) throws IOException {
        PatternCraftingMonitorNode node = new PatternCraftingMonitorNode(
                data.readItemIdentifierStack(),
                data.readInt(),
                data.readInt(),
                data.readBoolean());
        int childCount = data.readInt();
        for (int i = 0; i < childCount; i++) {
            node.addChild(PatternCraftingMonitorNode.readData(data));
        }
        return node;
    }
}
