package logisticspipes.crafting.pattern;

import net.minecraft.item.ItemStack;

public class ProcessingPattern extends AbstractPattern {

    public static final int INGREDIENT_SLOTS = 16;
    public static final int RESULT_SLOTS = 4;
    public static final int ITEM_SLOT_COUNT = INGREDIENT_SLOTS + RESULT_SLOTS;

    public ProcessingPattern(ItemStack patternStack) {
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
