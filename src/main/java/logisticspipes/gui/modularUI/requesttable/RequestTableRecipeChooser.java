package logisticspipes.gui.modularUI.requesttable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3NetworkEntry;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Takes a recipe from NEI for the crafting grid of the request table. An ingredient that can be one of several items,
 * like an ore dictionary one, has to be settled on one of them: a small window lets the player choose. A recipe without
 * such an ingredient goes into the grid at once.
 * <p>
 * Client only.
 */
public class RequestTableRecipeChooser {

    private static final int WIDTH = 150;
    private static final int TEXT_COLOR = 0x404040;
    private static final int SLOT = 18;
    private static final int GROUPS_PER_ROW = 3;
    private static final int GROUP_WIDTH = 44;
    private static final int STEP_BUTTON_WIDTH = 12;
    private static final int GROUP_HEIGHT = 24;
    private static final int GRID_X = (WIDTH - 3 * SLOT) / 2;
    private static final int GRID_Y = 20;
    private static final int GROUPS_Y = GRID_Y + 3 * SLOT + 8;
    // an amount in the network that is enough to settle on a variant without looking for a craftable one
    private static final int PLENTY = 64;

    /**
     * The items an ingredient can be and the one that is chosen. Slots of the grid that can be the same items share one
     * of these, so they are chosen together.
     */
    private static class Choice {

        final List<ItemStack> variants = new ArrayList<>();
        int position;

        ItemStack chosen() {
            return variants.get(position);
        }
    }

    private final RequestTableSyncHandler handler;
    private final IPanelHandler panelHandler;

    private final Choice[] grid = new Choice[9];
    private final List<Choice> groups = new ArrayList<>();
    // a recipe that waits for the window to open
    private boolean pending;

    public RequestTableRecipeChooser(ModularPanel parent, RequestTableSyncHandler handler) {
        this.handler = handler;
        this.panelHandler = IPanelHandler.simple(parent, this::build, true);
    }

    /**
     * Takes a recipe: for each of the nine slots of the grid the items it can hold, or null for an empty slot.
     */
    public void offer(ItemStack[][] candidates) {
        for (int i = 0; i < 9; i++) {
            grid[i] = null;
        }
        groups.clear();
        Map<List<ItemIdentifier>, Choice> byVariants = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            List<ItemStack> variants = expand(candidates[i]);
            if (variants.isEmpty()) {
                continue;
            }
            List<ItemIdentifier> key = new ArrayList<>(variants.size());
            for (ItemStack variant : variants) {
                key.add(ItemIdentifier.get(variant));
            }
            Choice choice = byVariants.get(key);
            if (choice == null) {
                choice = new Choice();
                choice.variants.addAll(variants);
                byVariants.put(key, choice);
                if (variants.size() > 1) {
                    groups.add(choice);
                }
            }
            grid[i] = choice;
        }
        if (groups.isEmpty()) {
            // nothing to choose
            confirm();
            return;
        }
        chooseMostLikely();
        // NEI still shows its own screen now, the window opens once the table is back
        pending = true;
    }

    /**
     * Called every tick while the GUI of the table is shown.
     */
    public void openPending() {
        if (!pending) {
            return;
        }
        if (panelHandler.isPanelOpen()) {
            // a window for an earlier recipe, it is built for other choices
            panelHandler.closePanel();
            return;
        }
        pending = false;
        panelHandler.deleteCachedPanel();
        panelHandler.openPanel();
    }

    /**
     * @return the distinct items of an ingredient; an item that stands for every damage value becomes its sub items
     */
    private static List<ItemStack> expand(ItemStack[] items) {
        List<ItemStack> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                List<ItemStack> subItems = new ArrayList<>();
                stack.getItem().getSubItems(stack.getItem(), stack.getItem().getCreativeTab(), subItems);
                if (subItems.isEmpty()) {
                    ItemStack plain = stack.copy();
                    plain.setItemDamage(0);
                    all.add(plain);
                } else {
                    all.addAll(subItems);
                }
            } else {
                all.add(stack);
            }
        }
        List<ItemIdentifier> seen = new ArrayList<>();
        for (ItemStack stack : all) {
            ItemStack single = stack.copy();
            single.stackSize = 1;
            ItemIdentifier item = ItemIdentifier.get(single);
            if (!seen.contains(item)) {
                seen.add(item);
                result.add(single);
            }
        }
        return result;
    }

    /**
     * Settles every choice on the item the network has most of. If it has plenty of none, an item it can craft is taken
     * instead.
     */
    private void chooseMostLikely() {
        for (Choice choice : groups) {
            int best = -1;
            int bestAmount = 0;
            int craftable = -1;
            for (int i = 0; i < choice.variants.size(); i++) {
                RequestTableMk3NetworkEntry entry = handler.getList()
                        .getEntry(ItemIdentifier.get(choice.variants.get(i)));
                if (entry == null) {
                    continue;
                }
                if (entry.getTotalAmount() > bestAmount) {
                    bestAmount = entry.getTotalAmount();
                    best = i;
                }
                if (craftable < 0 && entry.isCraftable()) {
                    craftable = i;
                }
            }
            if (bestAmount < PLENTY && craftable >= 0) {
                best = craftable;
            }
            if (best >= 0) {
                choice.position = best;
            }
        }
    }

    private void confirm() {
        ItemStack[] recipe = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            recipe[i] = grid[i] == null ? null : grid[i].chosen().copy();
        }
        handler.setRecipe(recipe);
    }

    private ModularPanel build(ModularPanel parent, EntityPlayer player) {
        int rows = (groups.size() + GROUPS_PER_ROW - 1) / GROUPS_PER_ROW;
        int buttonsY = GROUPS_Y + rows * GROUP_HEIGHT + 4;
        ModularPanel panel = new ModularPanel("request_table_recipe").size(WIDTH, buttonsY + 14 + 8)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);
        panel.child(IKey.lang("misc.selectOreDict").asWidget().pos(8, 7).color(TEXT_COLOR));
        panel.child(ButtonWidget.panelCloseButton());

        // the recipe as it will go into the grid
        for (int i = 0; i < 9; i++) {
            int slot = i;
            panel.child(new ChoiceSlot(() -> grid[slot], false).pos(GRID_X + (i % 3) * SLOT, GRID_Y + (i / 3) * SLOT));
        }

        // one slot for each group of ingredients that have a choice
        for (int i = 0; i < groups.size(); i++) {
            Choice choice = groups.get(i);
            // a button as tall as the slot on either side of it
            int x = (WIDTH - GROUPS_PER_ROW * GROUP_WIDTH) / 2 + (i % GROUPS_PER_ROW) * GROUP_WIDTH + 1;
            int y = GROUPS_Y + (i / GROUPS_PER_ROW) * GROUP_HEIGHT;
            panel.child(stepButton(choice, -1).pos(x, y));
            panel.child(new ChoiceSlot(() -> choice, true).pos(x + STEP_BUTTON_WIDTH, y));
            panel.child(stepButton(choice, 1).pos(x + STEP_BUTTON_WIDTH + SLOT, y));
        }

        panel.child(
                new ButtonWidget<>().pos(8, buttonsY).size(64, 14)
                        .overlay(IKey.lang("gui.requesttable.recipe.mostlikely")).onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            chooseMostLikely();
                            Interactable.playButtonClickSound();
                            return true;
                        }).tooltipBuilder(
                                tooltip -> tooltip.addLine(IKey.lang("gui.requesttable.recipe.mostlikely.tip"))));
        panel.child(
                new ButtonWidget<>().pos(WIDTH - 8 - 44, buttonsY).size(44, 14)
                        .overlay(IKey.lang("gui.requesttable.recipe.done")).onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            Interactable.playButtonClickSound();
                            panelHandler.closePanel();
                            confirm();
                            return true;
                        }));
        return panel;
    }

    private static void step(Choice choice, int by) {
        int size = choice.variants.size();
        choice.position = ((choice.position + by) % size + size) % size;
    }

    private ButtonWidget<?> stepButton(Choice choice, int by) {
        return new ButtonWidget<>().size(STEP_BUTTON_WIDTH, SLOT)
                .overlay((context, x, y, width, height, widgetTheme) -> drawStepArrow(x, y, width, height, by > 0))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    step(choice, by);
                    Interactable.playButtonClickSound();
                    return true;
                });
    }

    /**
     * Draws a white triangle with the shadow of the other icons into the middle of the given area.
     */
    private static void drawStepArrow(int x, int y, int width, int height, boolean right) {
        int left = x + (width - 4) / 2;
        int top = y + (height - 7) / 2;
        for (int pass = 0; pass < 2; pass++) {
            int offset = pass == 0 ? 1 : 0;
            int color = pass == 0 ? 0xff3f3f3f : 0xffffffff;
            for (int column = 0; column < 4; column++) {
                // the longest column is the base of the triangle
                int inset = right ? column : 3 - column;
                GuiDraw.drawRect(left + column + offset, top + inset + offset, 1, 7 - 2 * inset, color);
            }
        }
    }

    /**
     * Shows the chosen item of a choice. One that belongs to a group steps through the items with a left or right
     * click.
     */
    private static class ChoiceSlot extends Widget<ChoiceSlot> implements Interactable {

        private final Supplier<Choice> choice;
        private final boolean clickable;
        private final RichTooltip itemTooltip = new RichTooltip().parent(this).tooltipBuilder(this::buildItemTooltip);
        private ItemStack shown;

        ChoiceSlot(Supplier<Choice> choice, boolean clickable) {
            this.choice = choice;
            this.clickable = clickable;
            size(SLOT, SLOT);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            GuiTextures.SLOT_ITEM.draw(context, 0, 0, SLOT, SLOT, widgetTheme.getTheme());
            Choice current = choice.get();
            if (current == null) {
                return;
            }
            GuiDraw.drawItem(current.chosen(), 1, 1, 16, 16, context.getCurrentDrawingZ());
            if (isHovering()) {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GuiDraw.drawRect(1, 1, 16, 16, 0x80ffffff);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glColor4f(1, 1, 1, 1);
            }
        }

        @Override
        public void drawForeground(ModularGuiContext context) {
            Choice current = choice.get();
            if (current == null || !isHovering()) {
                return;
            }
            if (shown != current.chosen()) {
                shown = current.chosen();
                itemTooltip.markDirty();
            }
            itemTooltip.draw(context, shown);
        }

        private void buildItemTooltip(RichTooltip tooltip) {
            Choice current = choice.get();
            if (current == null) {
                return;
            }
            tooltip.addFromItem(current.chosen());
            if (current.variants.size() > 1) {
                tooltip.addLine(
                        EnumChatFormatting.GRAY.toString() + (current.position + 1) + " / " + current.variants.size());
            }
        }

        @Override
        public @NotNull Result onMousePressed(int mouseButton) {
            Choice current = choice.get();
            if (!clickable || current == null || (mouseButton != 0 && mouseButton != 1)) {
                return Result.IGNORE;
            }
            step(current, mouseButton == 0 ? 1 : -1);
            Interactable.playButtonClickSound();
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
            Choice current = choice.get();
            if (!clickable || current == null) {
                return false;
            }
            step(current, -scrollDirection.modifier);
            return true;
        }
    }
}
