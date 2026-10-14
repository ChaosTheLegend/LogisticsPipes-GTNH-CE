package logisticspipes.gui.modularUI.requesttable;

import java.util.regex.Matcher;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldHandler;
import com.cleanroommc.modularui.widgets.textfield.TextFieldRenderer;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.common.Loader;
import logisticspipes.crafting.requesttable.RequestTableNetworkList;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * The search field of the request table. A right click clears it, and an item dragged from NEI onto it or carried from
 * the inventory and clicked on it puts the name of the item into it.
 */
public class RequestTableSearchField extends TextFieldWidget implements RecipeViewerGhostIngredientSlot<ItemStack> {

    // the color NEI shows a word that searches the mod in
    private static final int MOD_WORD_COLOR = 0xffff55ff;

    public RequestTableSearchField() {
        renderer = new Renderer(handler);
        handler.setRenderer(renderer);
    }

    /**
     * Draws the words that search the mod, the ones that start with {@code @}, in a color of their own.
     */
    private static final class Renderer extends TextFieldRenderer {

        private Renderer(TextFieldHandler handler) {
            super(handler);
        }

        @Override
        protected void draw(String text, float x, float y) {
            if (simulate || text.indexOf('@') < 0) {
                super.draw(text, x, y);
                return;
            }
            int textColor = color;
            int drawn = 0;
            Matcher words = RequestTableNetworkList.SEARCH_WORD.matcher(text);
            while (words.find()) {
                if (!words.group().startsWith("@")) {
                    continue;
                }
                drawPart(text, drawn, words.start(), x, y);
                color = MOD_WORD_COLOR;
                drawPart(text, words.start(), words.end(), x, y);
                color = textColor;
                drawn = words.end();
            }
            drawPart(text, drawn, text.length(), x, y);
        }

        /**
         * Draws a part of the text where it stands in the whole of it.
         */
        private void drawPart(String text, int start, int end, float x, float y) {
            if (start < end) {
                super.draw(
                        text.substring(start, end),
                        x + getFontRenderer().getStringWidth(text.substring(0, start)) * scale,
                        y);
            }
        }
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        // an item carried from the inventory puts its name in like one dragged from NEI, and stays on the cursor
        ItemStack held = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
        if (held != null && (mouseButton == 0 || mouseButton == 1)) {
            String name = getSearchName(held);
            if (name != null && !name.isEmpty()) {
                replaceText(name);
                return Result.SUCCESS;
            }
        }
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
