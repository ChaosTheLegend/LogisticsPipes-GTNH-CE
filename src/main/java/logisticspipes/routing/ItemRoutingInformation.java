package logisticspipes.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.crafting.PatternCraftingReference;
import logisticspipes.crafting.PatternTargetInformation;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.order.IDistanceTracker;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;
import lombok.Setter;

public class ItemRoutingInformation {

    private static final String TARGET_INFO_TAG = "targetInfo";
    private static final String TARGET_INFO_TYPE_TAG = "type";
    private static final String TARGET_INFO_PATTERN = "pattern";
    private static final String TARGET_PATTERN_SLOT_TAG = "patternSlot";
    private static final String TARGET_INPUT_SLOT_TAG = "inputSlot";
    private static final String TARGET_ORDER_REFERENCE_PREFIX = "order";
    private static final String TARGET_DELIVERY_REFERENCE_PREFIX = "delivery";

    public static class DelayComparator implements Comparator<ItemRoutingInformation> {

        @Override
        public int compare(ItemRoutingInformation o1, ItemRoutingInformation o2) {
            return (int) (o2.getTimeOut() - o1.getTimeOut()); // cast will never overflow because the delta is in
            // 1/20ths of a second.
        }
    }

    @Override
    public ItemRoutingInformation clone() {
        ItemRoutingInformation that = new ItemRoutingInformation();
        that.destinationint = destinationint;
        that.destinationUUID = destinationUUID;
        that.arrived = arrived;
        that.bufferCounter = bufferCounter;
        that._doNotBuffer = _doNotBuffer;
        that._transportMode = _transportMode;
        that.jamlist = new ArrayList<>(jamlist);
        that.tracker = tracker;
        that.targetInfo = targetInfo;
        that.item = getItem().clone();
        return that;
    }

    public int destinationint = -1;
    public UUID destinationUUID;
    public boolean arrived;
    public int bufferCounter = 0;
    public boolean _doNotBuffer;
    public TransportMode _transportMode = TransportMode.Unknown;
    public List<Integer> jamlist = new ArrayList<>();
    public IDistanceTracker tracker = null;
    public IAdditionalTargetInformation targetInfo;

    private long delay = 640 + MainProxy.getGlobalTick();

    @Getter
    @Setter
    private ItemIdentifierStack item;

    public void readFromNBT(NBTTagCompound nbttagcompound) {
        if (nbttagcompound.hasKey("destinationUUID")) {
            destinationUUID = UUID.fromString(nbttagcompound.getString("destinationUUID"));
        }
        arrived = nbttagcompound.getBoolean("arrived");
        bufferCounter = nbttagcompound.getInteger("bufferCounter");
        _transportMode = TransportMode.values()[nbttagcompound.getInteger("transportMode")];
        ItemStack stack = ItemStack.loadItemStackFromNBT(nbttagcompound.getCompoundTag("Item"));
        if (stack != null) {
            setItem(ItemIdentifierStack.getFromStack(stack));
        }
        targetInfo = readTargetInfo(nbttagcompound.getCompoundTag(TARGET_INFO_TAG));
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        if (destinationUUID != null) {
            nbttagcompound.setString("destinationUUID", destinationUUID.toString());
        }
        nbttagcompound.setBoolean("arrived", arrived);
        nbttagcompound.setInteger("bufferCounter", bufferCounter);
        nbttagcompound.setInteger("transportMode", _transportMode.ordinal());

        NBTTagCompound nbttagcompound2 = new NBTTagCompound();
        getItem().makeNormalStack().writeToNBT(nbttagcompound2);
        nbttagcompound.setTag("Item", nbttagcompound2);
        NBTTagCompound targetTag = writeTargetInfo(targetInfo);
        if (!targetTag.hasNoTags()) {
            nbttagcompound.setTag(TARGET_INFO_TAG, targetTag);
        }
    }

    private IAdditionalTargetInformation readTargetInfo(NBTTagCompound tag) {
        if (!TARGET_INFO_PATTERN.equals(tag.getString(TARGET_INFO_TYPE_TAG))) {
            return null;
        }
        int inputSlot = tag.hasKey(TARGET_INPUT_SLOT_TAG) ? tag.getInteger(TARGET_INPUT_SLOT_TAG)
                : PatternTargetInformation.NO_INPUT_SLOT;
        return new PatternTargetInformation(
                tag.getInteger(TARGET_PATTERN_SLOT_TAG),
                inputSlot,
                PatternCraftingReference.readFromNBT(tag, TARGET_ORDER_REFERENCE_PREFIX),
                PatternCraftingReference.readFromNBT(tag, TARGET_DELIVERY_REFERENCE_PREFIX));
    }

    private NBTTagCompound writeTargetInfo(IAdditionalTargetInformation info) {
        NBTTagCompound tag = new NBTTagCompound();
        if (info instanceof PatternTargetInformation patternInfo) {
            tag.setString(TARGET_INFO_TYPE_TAG, TARGET_INFO_PATTERN);
            tag.setInteger(TARGET_PATTERN_SLOT_TAG, patternInfo.patternSlot());
            tag.setInteger(TARGET_INPUT_SLOT_TAG, patternInfo.inputSlot());
            if (patternInfo.orderReference() != null) {
                patternInfo.orderReference().writeToNBT(tag, TARGET_ORDER_REFERENCE_PREFIX);
            }
            if (patternInfo.deliveryReference() != null) {
                patternInfo.deliveryReference().writeToNBT(tag, TARGET_DELIVERY_REFERENCE_PREFIX);
            }
        }
        return tag;
    }

    // the global LP tick in which getTickToTimeOut returns 0.
    public long getTimeOut() {
        return delay;
    }

    // how many ticks until this times out
    public long getTickToTimeOut() {
        return delay - MainProxy.getGlobalTick();
    }

    public void resetDelay() {
        delay = 640 + MainProxy.getGlobalTick();
        if (tracker != null) {
            tracker.setDelay(delay);
        }
    }

    @Override
    public String toString() {
        return "(" + item
                + ", "
                + destinationint
                + ", "
                + destinationUUID
                + ", "
                + _transportMode
                + ", "
                + jamlist
                + ", "
                + delay
                + ", "
                + tracker;
    }
}
