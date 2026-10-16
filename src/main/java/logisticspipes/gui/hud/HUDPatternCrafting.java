package logisticspipes.gui.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.crafting.PatternCraftingHudState.IngredientInfo;
import logisticspipes.crafting.PatternCraftingHudState.OutputInfo;
import logisticspipes.crafting.PatternCraftingHudState.PatternInfo;
import logisticspipes.interfaces.IHUDConfig;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.SimpleGraphics;
import logisticspipes.utils.gui.hud.BasicHUDButton;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;
import logisticspipes.utils.string.StringUtils;

public class HUDPatternCrafting extends BasicHUDGui {

    private static final int WINDOW_LEFT = -58;
    private static final int WINDOW_TOP = -42;
    private static final int WINDOW_RIGHT = 58;
    private static final int WINDOW_BOTTOM = 50;
    private static final int HEADER_LEFT = WINDOW_LEFT + 14;
    private static final int HEADER_TOP = WINDOW_TOP + 6;
    private static final int INGREDIENT_LEFT = -51;
    private static final int INGREDIENT_TOP = -23;
    private static final int OUTPUT_LEFT = 36;
    private static final int OUTPUT_TOP = -23;
    private static final int ARROW_X = 13;
    private static final int ARROW_Y = 2;
    private static final int STATUS_TOP = 35;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_BACKGROUND_OFFSET = -1;
    private static final int BUFFER_BLUE = 0xff55aaff;
    private static final int TEXT_COLOR = 0;
    private static final int MAX_STATUS_LINES = 2;
    private static final int STATUS_LINE_HEIGHT = 8;
    private static final float TEXT_SCALE = 0.75F;
    private static final float HEADER_SCALE = TEXT_SCALE;
    private static final float ITEM_SCALE_X = 0.8F;
    private static final float ITEM_SCALE_Y = 0.8F;
    private static final float ITEM_SCALE_Z = -0.0001F;

    private final PipeItemsPatternCraftingLogistics pipe;
    private int page = 0;

    public HUDPatternCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        addButton(new BasicHUDButton("<", WINDOW_LEFT + 4, WINDOW_TOP + 4, 8, 8) {

            @Override
            public void clicked() {
                if (page > 0) {
                    page--;
                }
            }

            @Override
            public boolean shouldRenderButton() {
                return getMaxPage() > 1;
            }

            @Override
            public boolean buttonEnabled() {
                return page > 0;
            }
        });
        addButton(new BasicHUDButton(">", WINDOW_RIGHT - 12, WINDOW_TOP + 4, 8, 8) {

            @Override
            public void clicked() {
                if (page + 1 < getMaxPage()) {
                    page++;
                }
            }

            @Override
            public boolean shouldRenderButton() {
                return getMaxPage() > 1;
            }

            @Override
            public boolean buttonEnabled() {
                return page + 1 < getMaxPage();
            }
        });
    }

    @Override
    public void renderHeadUpDisplay(double d, boolean day, boolean shifted, Minecraft mc, IHUDConfig config) {
        PatternCraftingHudState state = pipe.getHudState();
        List<PatternInfo> patterns = state.getPatterns();
        normalizePage(patterns.size());

        if (day) {
            GL11.glColor4b((byte) 64, (byte) 64, (byte) 64, (byte) 64);
        } else {
            GL11.glColor4b((byte) 127, (byte) 127, (byte) 127, (byte) 64);
        }
        GuiGraphics.drawGuiBackGround(mc, WINDOW_LEFT, WINDOW_TOP, WINDOW_RIGHT, WINDOW_BOTTOM, 0, false);
        if (day) {
            GL11.glColor4b((byte) 64, (byte) 64, (byte) 64, (byte) 127);
        } else {
            GL11.glColor4b((byte) 127, (byte) 127, (byte) 127, (byte) 127);
        }

        GL11.glTranslatef(0.0F, 0.0F, -0.01F);
        super.renderHeadUpDisplay(d, day, shifted, mc, config);
        GL11.glTranslatef(0.0F, 0.0F, -0.005F);

        var topString = "Mode: " + formatMode(state.getBlockingMode()) + " - ";

        if (patterns.isEmpty()) {
            topString += "0/0";
        } else {
            topString += (page + 1) + "/" + patterns.size();
        }

        drawScaledTrimmedString(
                mc.fontRenderer,
                topString,
                HEADER_LEFT,
                HEADER_TOP,
                WINDOW_RIGHT - WINDOW_LEFT - 32,
                HEADER_SCALE);

        if (patterns.isEmpty()) {
            return;
        }

        drawScaledString(mc.fontRenderer, "->", ARROW_X, ARROW_Y, TEXT_SCALE);
        PatternInfo pattern = patterns.get(page);

        GL11.glPushMatrix();
        GL11.glScalef(ITEM_SCALE_X, ITEM_SCALE_Y, ITEM_SCALE_Z);
        if (shifted) {
            renderSlotBackgrounds(mc, pattern);
        }
        ItemStackRenderer renderer = new ItemStackRenderer(0, 0, 100.0F, false, shifted, true);
        renderer.setScaleX(ITEM_SCALE_X).setScaleY(ITEM_SCALE_Y).setScaleZ(ITEM_SCALE_Z)
                .setDisplayAmount(DisplayAmount.ALWAYS);
        renderIngredients(mc, renderer, pattern.getIngredients());
        renderOutputs(mc, renderer, pattern.getOutputs());
        GL11.glPopMatrix();

        drawWrappedStatus(
                mc.fontRenderer,
                pattern.getStatus(),
                WINDOW_LEFT + 8,
                STATUS_TOP,
                WINDOW_RIGHT - WINDOW_LEFT - 16);
    }

    @Override
    public boolean display(IHUDConfig config) {
        return config.isHUDCrafting() && !pipe.getHudState().getPatterns().isEmpty();
    }

    @Override
    public boolean cursorOnWindow(int x, int y) {
        return WINDOW_LEFT < x && x < WINDOW_RIGHT && WINDOW_TOP < y && y < WINDOW_BOTTOM;
    }

    private void renderIngredients(Minecraft mc, ItemStackRenderer renderer, List<IngredientInfo> ingredients) {
        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
            IngredientInfo ingredient = ingredients.get(i);
            int slot = ingredient.slot() >= 0 ? ingredient.slot() : i;
            int x = INGREDIENT_LEFT + (slot % 3) * SLOT_SIZE;
            int y = INGREDIENT_TOP + (slot / 3) * SLOT_SIZE;
            renderStack(renderer, ingredient.stack(), x, y);
            if (ingredient.bufferedAmount() > 0) {
                drawHudAmount(mc, ingredient.bufferedAmount(), x, y);
            }
        }
    }

    private void renderSlotBackgrounds(Minecraft mc, PatternInfo pattern) {
        for (int i = 0; i < 9; i++) {
            int x = INGREDIENT_LEFT + (i % 3) * SLOT_SIZE;
            int y = INGREDIENT_TOP + (i / 3) * SLOT_SIZE;
            renderSlotBackground(mc, x, y);
        }

        int outputCount = Math.min(pattern.getOutputs().size(), 3);
        int firstY = getFirstOutputY(outputCount);
        for (int i = 0; i < outputCount; i++) {
            OutputInfo output = pattern.getOutputs().get(i);
            int slot = output.slot() >= 0 ? output.slot() : i;
            int y = output.slot() >= 0 ? OUTPUT_TOP + slot * SLOT_SIZE : firstY + i * SLOT_SIZE;
            renderSlotBackground(mc, OUTPUT_LEFT, y);
        }
    }

    private void renderSlotBackground(Minecraft mc, int x, int y) {
        GuiGraphics.drawSlotBackground(mc, x + SLOT_BACKGROUND_OFFSET, y + SLOT_BACKGROUND_OFFSET);
    }

    private void renderOutputs(Minecraft mc, ItemStackRenderer renderer, List<OutputInfo> outputs) {
        int outputCount = Math.min(outputs.size(), 3);
        int firstY = getFirstOutputY(outputCount);
        for (int i = 0; i < outputCount; i++) {
            OutputInfo output = outputs.get(i);
            int slot = output.slot() >= 0 ? output.slot() : i;
            int x = OUTPUT_LEFT;
            int y = output.slot() >= 0 ? OUTPUT_TOP + slot * SLOT_SIZE : firstY + slot * SLOT_SIZE;
            renderStack(renderer, output.stack(), x, y);
            if (output.requestedAmount() > 0) {
                drawHudAmount(mc, output.requestedAmount(), x, y);
            }
        }
    }

    private int getFirstOutputY(int outputCount) {
        return OUTPUT_TOP + (3 - outputCount) * SLOT_SIZE / 2;
    }

    private void renderStack(ItemStackRenderer renderer, ItemIdentifierStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        renderer.setItemIdentifierStack(stack).setPosX(x).setPosY(y);
        renderer.renderInGui();
    }

    private void drawHudAmount(Minecraft mc, int amount, int x, int y) {
        String amountString = StringUtils.getFormatedStackSize(amount, true);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glTranslatef(0.0F, 0.0F, 145.0F);
        GL11.glScalef(0.5F, 0.5F, 1.0F);
        SimpleGraphics.drawStringWithTranslatedShadow(
                mc.fontRenderer,
                amountString,
                (x + 17) * 2 - mc.fontRenderer.getStringWidth(amountString),
                (y - 2) * 2,
                BUFFER_BLUE);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void drawWrappedStatus(FontRenderer fontRenderer, String status, int x, int y, int width) {
        List<String> lines = wrap(
                fontRenderer,
                status == null ? "" : status,
                Math.round(width / TEXT_SCALE),
                MAX_STATUS_LINES);
        for (int i = 0; i < lines.size(); i++) {
            drawScaledString(fontRenderer, lines.get(i), x, y + i * STATUS_LINE_HEIGHT, TEXT_SCALE);
        }
    }

    private void drawScaledTrimmedString(FontRenderer fontRenderer, String text, int x, int y, int width, float scale) {
        String trimmed = trimToWidth(fontRenderer, text, Math.round(width / scale));
        drawScaledString(fontRenderer, trimmed, x, y, scale);
    }

    private void drawScaledString(FontRenderer fontRenderer, String text, int x, int y, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        fontRenderer.drawString(text, Math.round(x / scale), Math.round(y / scale), TEXT_COLOR);
        GL11.glPopMatrix();
    }

    private List<String> wrap(FontRenderer fontRenderer, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = text.trim();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            String line = remaining;
            while (fontRenderer.getStringWidth(line) > width && line.contains(" ")) {
                line = line.substring(0, line.lastIndexOf(' '));
            }
            if (fontRenderer.getStringWidth(line) > width) {
                line = trimToWidth(fontRenderer, line, width);
                remaining = "";
            } else {
                remaining = remaining.substring(line.length()).trim();
            }
            if (!remaining.isEmpty() && lines.size() + 1 == maxLines) {
                line = trimToWidth(fontRenderer, line, width);
                remaining = "";
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private String trimToWidth(FontRenderer fontRenderer, String text, int width) {
        if (fontRenderer.getStringWidth(text) <= width) {
            return text;
        }

        String ellipsis = "...";
        int availableWidth = Math.max(0, width - fontRenderer.getStringWidth(ellipsis));
        String result = text;
        while (!result.isEmpty() && fontRenderer.getStringWidth(result) > availableWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private String formatMode(PipeItemsPatternCraftingLogistics.BlockingMode mode) {
        return switch (mode) {
            case BLOCKING -> "Blocking";
            case SMART -> "Smart";
            default -> "Off";
        };
    }

    private void normalizePage(int size) {
        if (size <= 0) {
            page = 0;
        } else if (page >= size) {
            page = size - 1;
        }
    }

    private int getMaxPage() {
        return Math.max(1, pipe.getHudState().getPatterns().size());
    }
}
