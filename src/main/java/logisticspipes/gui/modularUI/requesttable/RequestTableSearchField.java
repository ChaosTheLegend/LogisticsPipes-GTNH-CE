package logisticspipes.gui.modularUI.requesttable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.common.Loader;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * The search field of the request table. A right click clears it, and an item dragged from NEI onto it puts the name of
 * the item into it.
 */
public class RequestTableSearchField extends TextFieldWidget implements RecipeViewerGhostIngredientSlot<ItemStack> {

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton == 1) {
            replaceText("");
            // fall through to the focus handling, so typing can go on right away
            return super.onMousePressed(0);
        }
        return super.onMousePressed(mouseButton);
    }

    @Override
    public boolean handleDragAndDrop(@NotNull ItemStack draggedStack, int button) {
        String name = getSearchName(draggedStack);
        if (name == null || name.isEmpty()) {
            return false;
        }
        replaceText(name);
        // tells NEI that the dragged stack was taken
        draggedStack.stackSize = 0;
        return true;
    }

    /**
     * The field takes the text of its value again whenever it has no focus, so both are set.
     */
    public void replaceText(String text) {
        getStringValue().setStringValue(text);
        setText(text);
        // show the start of the name, not its end
        handler.setCursor(0, 0, false);
    }

    /**
     * Gives the keyboard to the field, the search field of NEI lets go of it.
     */
    public void takeFocus() {
        if (isValid()) {
            getContext().focus(this);
            if (Loader.isModLoaded("NotEnoughItems")) {
                RequestTableNeiSearch.unfocus();
            }
        }
    }

    /**
     * The mouse wheel doesn't move the text sideways.
     */
    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        return false;
    }

    /**
     * @return the name the network list knows the stack by, the fluid name for a fluid container
     */
    private static String getSearchName(ItemStack stack) {
        if (stack.getItem() == null) {
            return null;
        }
        FluidStack fluid = SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
        if (fluid == null) {
            fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        }
        if (fluid != null && fluid.getFluid() != null) {
            return fluid.getLocalizedName();
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
    }
}
