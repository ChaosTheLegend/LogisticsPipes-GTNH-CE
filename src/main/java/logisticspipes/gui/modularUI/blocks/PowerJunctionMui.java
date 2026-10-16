package logisticspipes.gui.modularUI.blocks;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;

import logisticspipes.LPConstants;
import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.powertile.LogisticsPowerJunctionTileEntity;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.utils.string.StringUtils;

/**
 * ModularUI GUI of the power junction: the energy bar and level of the junction's network power pool above the player
 * inventory, plus a debug-only button that adds energy.
 */
public class PowerJunctionMui {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int BAR_X = 10;
    private static final int BAR_Y = 11;
    private static final int BAR_WIDTH = 5;
    private static final int BAR_HEIGHT = 59;

    private static final int TEXT_COLOR = 0x404040;
    private static final String LANG = "gui.powerjunction.";

    private static final UITexture BAR_EMPTY = barTexture(BAR_X, BAR_Y);
    private static final UITexture BAR_FULL = barTexture(176, 0);

    private final LogisticsPowerJunctionTileEntity tile;

    public PowerJunctionMui(LogisticsPowerJunctionTileEntity tile) {
        this.tile = tile;
    }

    private static UITexture barTexture(int x, int y) {
        return UITexture.builder().location(LogisticsPipes.rl("textures/gui/power_junction.png")).imageSize(256, 256)
                .xy(x, y, BAR_WIDTH, BAR_HEIGHT).build();
    }

    public ModularPanel buildUI(PanelSyncManager syncManager) {
        // the tile's getters read the pool on the server and are only polled there
        IntSyncValue stored = new IntSyncValue(tile::getPowerLevel);
        IntSyncValue capacity = new IntSyncValue(tile::getMaxStorage);
        syncManager.syncValue("stored", stored);
        syncManager.syncValue("capacity", capacity);

        ModularPanel panel = ModularPanel.defaultPanel("power_junction", WIDTH, HEIGHT)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);

        panel.child(IKey.lang(LANG + "LogisticsPowerJunction").asWidget().pos(30, 8).color(TEXT_COLOR));
        panel.child(
                new ProgressWidget().progress(() -> fraction(stored.getIntValue(), capacity.getIntValue()))
                        .texture(BAR_EMPTY, BAR_FULL, BAR_HEIGHT).direction(ProgressWidget.Direction.UP)
                        .pos(BAR_X, BAR_Y).size(BAR_WIDTH, BAR_HEIGHT));
        panel.child(IKey.lang(LANG + "NetworkPower").asWidget().pos(40, 23).color(TEXT_COLOR));
        panel.child(
                IKey.dynamic(() -> format("PowerLevel", stored.getIntValue())).asWidget().pos(40, 33)
                        .color(TEXT_COLOR));
        panel.child(
                IKey.dynamic(() -> format("MaxStorage", capacity.getIntValue())).asWidget().pos(40, 43)
                        .color(TEXT_COLOR));
        panel.child(IKey.lang(LANG + "ConversionEnergyRF").asWidget().pos(24, 58).color(TEXT_COLOR));
        panel.child(IKey.lang(LANG + "ConversionEnergyEU").asWidget().pos(100, 58).color(TEXT_COLOR));

        if (LPConstants.DEBUG) {
            panel.child(
                    new ButtonWidget<>().pos(140, 20).size(20, 20).overlay(IKey.str("+"))
                            .syncHandler(new InteractionSyncHandler().setOnMousePressed(i -> {
                                if (i.mouseButton == 0) tile.addEnergy(100000);
                            })));
        }

        panel.child(SlotGroupWidget.playerInventory(true));
        return panel;
    }

    private static double fraction(int stored, int capacity) {
        return capacity <= 0 ? 0 : (double) stored / capacity;
    }

    private static String format(String key, int value) {
        return String.format(StringUtils.translate(LANG + key), StringUtils.getStringWithSpacesFromInteger(value));
    }
}
