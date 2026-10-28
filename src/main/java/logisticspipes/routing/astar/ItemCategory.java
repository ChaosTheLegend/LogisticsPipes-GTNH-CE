package logisticspipes.routing.astar;

import java.util.List;

import net.minecraft.item.ItemStack;

import logisticspipes.modules.ModuleFluidContainerSink;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * A property of items a router can be interested in, next to specific items and all items. A module that takes items by
 * a property (their mod, "it holds fluid", ...) names the values it takes, and the {@link InterestRegistry} offers it
 * only the items with one of those values.
 * <p>
 * The values only pick the candidates, the module still decides in its sinksItem. So the values a module names must
 * cover everything it can take: wider is fine, narrower loses items.
 */
public enum ItemCategory {

    /**
     * Items that keep their fluid in their data (cells, capsules); the only value is {@link Boolean#TRUE}.
     */
    FLUID_HOLDING {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return ModuleFluidContainerSink.isFluidHoldingItem(item) ? FLAG : NONE;
        }
    },

    /**
     * The id of the mod that registered the item.
     */
    MOD {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return single(item.getModName());
        }
    },

    /**
     * The name of the creative tab the item is shown on.
     */
    CREATIVE_TAB {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return single(item.getCreativeTabName());
        }
    },

    /**
     * The {@link net.minecraft.item.Item} itself, whatever its damage and data.
     */
    ITEM {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return single(item.item);
        }
    },

    /**
     * IC2 electric items; the only value is {@link Boolean#TRUE}.
     */
    ELECTRIC {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return SimpleServiceLocator.IC2Proxy.isElectricItem(item.makeNormalStack(1)) ? FLAG : NONE;
        }
    },

    /**
     * Forestry bees, drones as well as princesses and queens; the only value is {@link Boolean#TRUE}.
     */
    BEE {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return SimpleServiceLocator.forestryProxy.isBee(item.makeNormalStack(1)) ? FLAG : NONE;
        }
    },

    /**
     * Items with enchantments on them (not enchanted books, they store theirs); the only value is {@link Boolean#TRUE}.
     */
    ENCHANTED {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            return item.makeNormalStack(1).isItemEnchanted() ? FLAG : NONE;
        }
    },

    /**
     * The tags of the Thaumcraft aspects the item has.
     */
    THAUMIC_ASPECT {

        @Override
        public Object[] computeKeys(ItemIdentifier item) {
            ItemStack stack = item.makeNormalStack(1);
            List<String> aspects = SimpleServiceLocator.thaumCraftProxy.getListOfTagsForStack(stack);
            return aspects == null || aspects.isEmpty() ? NONE : aspects.toArray();
        }
    };

    static final Object[] NONE = new Object[0];
    static final Object[] FLAG = { Boolean.TRUE };

    private static Object[] single(Object key) {
        return key == null ? NONE : new Object[] { key };
    }

    /**
     * Asked once per item and category: everyone goes through {@link ItemIdentifier#getCategoryKeys(ItemCategory)},
     * which keeps the answer. So it may only depend on what the identifier is, not on the world.
     *
     * @return the values of the property the item has, an empty array if it has none
     */
    public abstract Object[] computeKeys(ItemIdentifier item);
}
