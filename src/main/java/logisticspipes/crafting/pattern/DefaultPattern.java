package logisticspipes.crafting.pattern;

import net.minecraft.item.ItemStack;

public class DefaultPattern extends AbstractPattern {

    public static final int INGREDIENT_SLOTS = 9;
    public static final int RESULT_SLOTS = 3;
    public static final int ITEM_SLOT_COUNT = INGREDIENT_SLOTS + RESULT_SLOTS;

    public DefaultPattern(ItemStack patternStack) {
        super(patternStack);
    }

    @Override
    public int getIngredientSlotCount() {
        return INGREDIENT_SLOTS;
    }

    @Override
    public int getResultSlotCount() {
        return RESULT_SLOTS;
    }
}
