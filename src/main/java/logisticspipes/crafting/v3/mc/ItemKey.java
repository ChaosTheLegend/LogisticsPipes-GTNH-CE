package logisticspipes.crafting.v3.mc;

import logisticspipes.crafting.v3.model.MatchRule;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.utils.item.DictItemIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/** An exact item: item, damage and NBT, through LP's interned {@link ItemIdentifier}. */
public final class ItemKey implements ResourceKey {

    public final ItemIdentifier id;

    private ItemKey(ItemIdentifier id) {
        this.id = id;
    }

    public static ItemKey of(ItemIdentifier id) {
        return id == null ? null : new ItemKey(id);
    }

    @Override
    public boolean isFluid() {
        return false;
    }

    @Override
    public int maxStackSize() {
        return Math.max(1, id.getMaxStackSize());
    }

    @Override
    public String displayName() {
        return id.getFriendlyName();
    }

    @Override
    public boolean matches(ResourceKey other, MatchRule rule, String oreName) {
        if (!(other instanceof ItemKey)) return false;
        ItemIdentifier o = ((ItemKey) other).id;
        if (id.equals(o)) return true;
        switch (rule) {
            case IGNORE_NBT:
                return id.equalsWithoutNBT(o);
            case IGNORE_DAMAGE:
                return id.item == o.item
                        && id.getIgnoringData().getIgnoringNBT().equals(o.getIgnoringData().getIgnoringNBT());
            case ORE_DICT: {
                if (id.equalsWithoutNBT(o)) return true;
                DictItemIdentifier mine = id.getDictIdentifiers();
                DictItemIdentifier theirs = o.getDictIdentifiers();
                return mine != null && theirs != null && mine.canMatch(theirs, false, false);
            }
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemKey && ((ItemKey) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
