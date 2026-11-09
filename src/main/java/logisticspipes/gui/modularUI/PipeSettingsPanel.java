package logisticspipes.gui.modularUI;

import java.util.function.Supplier;

import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.PipeSideGrid;
import logisticspipes.pipes.basic.PipeSideGrid.SideState;
import logisticspipes.pipes.upgrades.SneakyFaces;

/**
 * The settings window of a pipe or module, opened with the gear button. What it shows depends on where it is opened:
 * <ul>
 * <li>the six sides of the pipe as a cross (left click connects / disconnects, right click makes it the working side),
 * only for a pipe;</li>
 * <li>the faces of the sneaky upgrade as a second cross, when one is installed: the faces of the inventory next to the
 * pipe to work through, in order (the number on a face); a click adds or removes a face;</li>
 * <li>the priority of the module, when a priority upgrade is installed.</li>
 * </ul>
 * The values are registered with the window the button sits in, the panel only shows them.
 */
public final class PipeSettingsPanel {

    // the cross: column and row of each side
    private static final ForgeDirection[] CROSS_SIDES = { ForgeDirection.UP, ForgeDirection.WEST, ForgeDirection.NORTH,
            ForgeDirection.EAST, ForgeDirection.DOWN, ForgeDirection.SOUTH };
    private static final int[][] CROSS_CELLS = { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, { 1, 2 }, { 2, 2 } };
    private static final int CELL = 20;
    public static final String BUTTON_NAME = "lp_settings";
    public static final String PANEL_NAME = "pipe_settings";
    private static final int WIDTH = 140;
    // the width with both crosses side by side
    private static final int WIDE = 160;
    // the synced sneaky faces: SneakyFaces.encode, this bit set when a sneaky upgrade is installed
    private static final int SNEAKY_ITEM_BIT = 1 << 24;
    // set in the same value when a priority upgrade is installed
    private static final int PRIORITY_ITEM_BIT = 1 << 25;
    private static final int TEXT_COLOR = 0x404040;
    private static final String LANG = "gui.pipesettings.";

    // the synced side states: 2 bits per side (SideState ordinal), the working side ordinal at 12, has one at 15
    private static final int WORKING_SHIFT = 12;
    private static final int HAS_WORKING_BIT = 1 << 15;

    private PipeSettingsPanel() {}

    /**
     * Registers the values with the window (both sides build it) and returns the gear button that opens the panel.
     *
     * @param parent      the window's panel
     * @param syncManager the window's sync manager
     * @param id          unique for the window
     * @param pipe        the pipe whose sides are shown, null for none (a module in hand)
     * @param module      the module whose priority is shown, null for none; it can change (chassis tabs)
     */
    public static ButtonWidget<?> button(ModularPanel parent, PanelSyncManager syncManager, String id,
            CoreRoutedPipe pipe, Supplier<? extends LogisticsModule> module) {
        IntSyncValue sides = null;
        InteractionSyncHandler[] clicks = new InteractionSyncHandler[6];
        if (pipe != null) {
            sides = syncManager.getOrCreateSyncHandler(
                    id + "_sides",
                    IntSyncValue.class,
                    () -> new IntSyncValue(() -> encode(pipe)));
            if (!syncManager.isClient()) {
                // a value no pipe has: the server sends the real one with the next update
                sides.setIntValue(-1, false, false);
            }
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                InteractionSyncHandler click = syncManager.getOrCreateSyncHandler(
                        id + "_side_click",
                        side.ordinal(),
                        InteractionSyncHandler.class,
                        InteractionSyncHandler::new);
                // runs on both sides: only the server changes the pipe, the client gets the new state synced
                click.setOnMousePressed(mouse -> {
                    if (syncManager.isClient()) return;
                    if (mouse.mouseButton == 0) {
                        pipe.toggleSideDisconnected(side);
                    } else if (mouse.mouseButton == 1) {
                        pipe.setWorkingSide(side);
                    }
                });
                clicks[side.ordinal()] = click;
            }
        }
        IntSyncValue priority = module == null ? null : PriorityWidgets.value(syncManager, id, module);

        // the sneaky upgrade of the module (its slots, or the pipe's for a module that uses those), else of the pipe
        Supplier<ISlotUpgradeManager> sneakyHolder = () -> sneakyHolder(pipe, module);
        IntSyncValue sneaky = syncManager.getOrCreateSyncHandler(
                id + "_sneaky",
                IntSyncValue.class,
                () -> new IntSyncValue(() -> encodeSneaky(sneakyHolder.get())));
        if (!syncManager.isClient()) {
            sneaky.setIntValue(-1, false, false);
        }
        InteractionSyncHandler[] sneakyClicks = new InteractionSyncHandler[6];
        for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            InteractionSyncHandler click = syncManager.getOrCreateSyncHandler(
                    id + "_sneaky_click",
                    face.ordinal(),
                    InteractionSyncHandler.class,
                    InteractionSyncHandler::new);
            // runs on both sides: only the server changes the faces, the client gets them synced
            click.setOnMousePressed(mouse -> {
                if (syncManager.isClient() || mouse.mouseButton != 0) return;
                ISlotUpgradeManager holder = sneakyHolder.get();
                if (holder != null && holder.hasSneakyItem()) {
                    holder.setSneakyFaces(SneakyFaces.toggle(holder.getSneakyFaces(), face));
                    if (pipe != null && pipe.container != null) {
                        pipe.container.markDirty();
                    }
                }
            });
            sneakyClicks[face.ordinal()] = click;
        }

        IntSyncValue finalSides = sides;
        // the upgrades the open panel was built with (the sneaky and priority bits)
        int[] builtWith = { 0 };
        IPanelHandler handler = IPanelHandler.simple(parent, (panel, player) -> {
            builtWith[0] = upgradeBits(sneaky);
            return build(
                    finalSides,
                    clicks,
                    sneaky,
                    sneakyClicks,
                    priority,
                    module,
                    pipe != null && pipe.hasWorkingSide());
        }, true);
        if (syncManager.isClient()) {
            // a sneaky or priority upgrade put in or taken out while the panel is open: build it again
            sneaky.setChangeListener(() -> {
                if (handler.isPanelOpen() && upgradeBits(sneaky) != builtWith[0]) {
                    handler.closePanel();
                    handler.deleteCachedPanel();
                    handler.openPanel();
                }
            });
        }
        // the name lets tools find the button (the local UI test opens the panel with it)
        return new ButtonWidget<>().name(BUTTON_NAME).size(16, 16).overlay(GuiTextures.GEAR.asIcon().size(12))
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    // built anew every time: what it shows depends on the upgrades installed now
                    handler.deleteCachedPanel();
                    handler.openPanel();
                    return true;
                }).tooltip(tooltip -> tooltip.addLine(IKey.lang(LANG + "button.tip")));
    }

    private static ModularPanel build(IntSyncValue sides, InteractionSyncHandler[] clicks, IntSyncValue sneaky,
            InteractionSyncHandler[] sneakyClicks, IntSyncValue priority, Supplier<? extends LogisticsModule> module,
            boolean hasWorkingSide) {
        boolean showSides = sides != null;
        // the faces show while a sneaky upgrade is installed when the window opens
        boolean showSneaky = hasSneakyItem(sneaky);
        int crosses = (showSides ? 1 : 0) + (showSneaky ? 1 : 0);
        int width = crosses == 2 ? WIDE : WIDTH;
        int crossHeight = crosses == 0 ? 0 : 12 + CELL * 3 + 24;
        // the priority shows while a priority upgrade is installed
        boolean showPriority = priority != null && (upgradeBits(sneaky) & PRIORITY_ITEM_BIT) != 0;
        int height = 22 + crossHeight + (showPriority ? 20 : 0) + 6;
        ModularPanel panel = new ModularPanel(PANEL_NAME).size(width, height)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);
        panel.child(IKey.lang(LANG + "title").asWidget().pos(8, 7).color(TEXT_COLOR));
        panel.child(ButtonWidget.panelCloseButton());

        int y = 20;
        if (crosses > 0) {
            // one cross centred, two side by side
            int slot = crosses == 2 ? (width - 16) / 2 : width - 16;
            int left = 8;
            if (showSides) {
                sidesCross(panel, sides, clicks, hasWorkingSide, left, slot, y);
                left += slot;
            }
            if (showSneaky) {
                sneakyCross(panel, sneaky, sneakyClicks, left, slot, y);
            }
            y += crossHeight;
        }
        if (showPriority) {
            panel.child(
                    PriorityWidgets.row(priority, IKey.lang("gui.module.priority")).pos(8, y)
                            .setEnabledIf(w -> module.get() instanceof IPrioritizedModule));
        }
        return panel;
    }

    // the sides of the pipe: connected, disconnected, the working side
    private static void sidesCross(ModularPanel panel, IntSyncValue sides, InteractionSyncHandler[] clicks,
            boolean hasWorkingSide, int x, int width, int y) {
        panel.child(IKey.lang(LANG + "sides").scale(0.8f).asWidget().pos(x, y).color(TEXT_COLOR));
        int left = x + (width - CELL * 3) / 2;
        int top = y + 12;
        for (int i = 0; i < CROSS_SIDES.length; i++) {
            ForgeDirection side = CROSS_SIDES[i];
            InteractionSyncHandler click = clicks[side.ordinal()];
            panel.child(
                    new ButtonWidget<>().pos(left + CROSS_CELLS[i][0] * CELL, top + CROSS_CELLS[i][1] * CELL)
                            .size(CELL - 2, CELL - 2).background(sideBackground(sides, side))
                            .overlay(IKey.str(side.name().substring(0, 1)))
                            .onMousePressed(mouse -> click.onMousePressed(mouse)).tooltipAutoUpdate(true)
                            .tooltipBuilder(tooltip -> {
                                tooltip.addLine(IKey.lang(LANG + "side.tip", sideName(side), stateText(sides, side)));
                                tooltip.addLine(IKey.lang(LANG + "sides.left"));
                                if (hasWorkingSide) {
                                    tooltip.addLine(IKey.lang(LANG + "sides.right"));
                                }
                            }));
        }
        legend(
                panel,
                x,
                top + CELL * 3 + 2,
                LANG + "sides.legend",
                hasWorkingSide ? LANG + "sides.legend.working" : null);
    }

    // the faces of the sneaky upgrade, numbered in their order
    private static void sneakyCross(ModularPanel panel, IntSyncValue sneaky, InteractionSyncHandler[] clicks, int x,
            int width, int y) {
        panel.child(IKey.lang(LANG + "sneaky").scale(0.8f).asWidget().pos(x, y).color(TEXT_COLOR));
        int left = x + (width - CELL * 3) / 2;
        int top = y + 12;
        for (int i = 0; i < CROSS_SIDES.length; i++) {
            ForgeDirection face = CROSS_SIDES[i];
            InteractionSyncHandler click = clicks[face.ordinal()];
            panel.child(
                    new ButtonWidget<>().pos(left + CROSS_CELLS[i][0] * CELL, top + CROSS_CELLS[i][1] * CELL)
                            .size(CELL - 2, CELL - 2).background(faceBackground(sneaky, face))
                            .overlay(IKey.dynamic(() -> faceLabel(sneaky, face)))
                            .onMousePressed(mouse -> click.onMousePressed(mouse)).tooltipAutoUpdate(true)
                            .tooltipBuilder(tooltip -> {
                                int order = faceOrder(sneaky, face);
                                tooltip.addLine(
                                        order > 0 ? IKey.lang(LANG + "face.used", sideName(face), ordinal(order))
                                                : IKey.lang(LANG + "face.unused", sideName(face)));
                                tooltip.addLine(IKey.lang(LANG + "sneaky.tip1"));
                                tooltip.addLine(IKey.lang(LANG + "sneaky.tip2"));
                                tooltip.addLine(IKey.lang(LANG + "sneaky.tip3"));
                            }));
        }
        legend(panel, x, top + CELL * 3 + 2, LANG + "sneaky.legend", LANG + "sneaky.legend2");
    }

    // one or two short lines under a cross, lang keys
    private static void legend(ModularPanel panel, int x, int y, String first, String second) {
        panel.child(IKey.lang(first).scale(0.6f).asWidget().pos(x, y).color(TEXT_COLOR));
        if (second != null) {
            panel.child(IKey.lang(second).scale(0.6f).asWidget().pos(x, y + 7).color(TEXT_COLOR));
        }
    }

    private static IDrawable faceBackground(IntSyncValue sneaky, ForgeDirection face) {
        return (context, x, y, width, height, theme) -> {
            int color = faceOrder(sneaky, face) > 0 ? 0xFF4070D0 : 0xFF8B8B8B;
            GuiDraw.drawRect(x, y, width, height, 0xFF373737);
            GuiDraw.drawRect(x + 1, y + 1, width - 2, height - 2, color);
        };
    }

    private static String faceLabel(IntSyncValue sneaky, ForgeDirection face) {
        int order = faceOrder(sneaky, face);
        String letter = face.name().substring(0, 1);
        return order > 0 ? letter + order : letter;
    }

    // 1 for the first face, 0 for a face not used
    private static int faceOrder(IntSyncValue sneaky, ForgeDirection face) {
        int value = sneaky.getIntValue();
        if (value < 0) {
            return 0;
        }
        ForgeDirection[] faces = SneakyFaces.decode(value & (SNEAKY_ITEM_BIT - 1));
        for (int i = 0; i < faces.length; i++) {
            if (faces[i] == face) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String ordinal(int n) {
        return n <= 3 ? translate(LANG + "ordinal." + n) : translate(LANG + "ordinal.n", n);
    }

    // a translated part of a longer text, on the client
    private static String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private static int upgradeBits(IntSyncValue sneaky) {
        int value = sneaky.getIntValue();
        return value < 0 ? 0 : value & (SNEAKY_ITEM_BIT | PRIORITY_ITEM_BIT);
    }

    private static boolean hasSneakyItem(IntSyncValue sneaky) {
        int value = sneaky.getIntValue();
        return value >= 0 && (value & SNEAKY_ITEM_BIT) != 0;
    }

    private static int encodeSneaky(ISlotUpgradeManager holder) {
        if (holder == null) {
            return 0;
        }
        return SneakyFaces.encode(holder.getSneakyFaces()) | (holder.hasSneakyItem() ? SNEAKY_ITEM_BIT : 0)
                | (holder.hasPriorityUpgrade() ? PRIORITY_ITEM_BIT : 0);
    }

    /**
     * @return the upgrades the sneaky upgrade of this window is in: those the module uses (its own slots, or the pipe's
     *         for a module that has none), the module's own for a module in hand, else the pipe's
     */
    private static ISlotUpgradeManager sneakyHolder(CoreRoutedPipe pipe, Supplier<? extends LogisticsModule> module) {
        LogisticsModule current = module == null ? null : module.get();
        if (current != null) {
            if (current.getService() != null && current.getSlot() != null) {
                return current.getService().getUpgradeManager(current.getSlot(), current.getPositionInt());
            }
            return current.getModuleUpgrades();
        }
        return pipe == null ? null : pipe.getOriginalUpgradeManager();
    }

    private static IDrawable sideBackground(IntSyncValue sides, ForgeDirection side) {
        return (context, x, y, width, height, theme) -> {
            int color;
            if (isWorking(sides, side)) {
                color = 0xFFE0C020;
            } else {
                color = switch (state(sides, side)) {
                    case CONNECTED -> 0xFF40A040;
                    case DISCONNECTED -> 0xFFC04040;
                    default -> 0xFF8B8B8B;
                };
            }
            GuiDraw.drawRect(x, y, width, height, 0xFF373737);
            GuiDraw.drawRect(x + 1, y + 1, width - 2, height - 2, color);
        };
    }

    private static String stateText(IntSyncValue sides, ForgeDirection side) {
        String text = translate(LANG + "state." + state(sides, side).name().toLowerCase());
        return isWorking(sides, side) ? translate(LANG + "state.working", text) : text;
    }

    private static String sideName(ForgeDirection side) {
        return translate(LANG + "side." + side.name().toLowerCase());
    }

    private static int encode(CoreRoutedPipe pipe) {
        int value = 0;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            value |= PipeSideGrid.stateOf(pipe, side).ordinal() << (side.ordinal() * 2);
        }
        if (pipe.hasWorkingSide()) {
            value |= HAS_WORKING_BIT;
            value |= pipe.getWorkingSide().ordinal() << WORKING_SHIFT;
        }
        return value;
    }

    private static SideState state(IntSyncValue sides, ForgeDirection side) {
        int value = sides.getIntValue();
        if (value < 0) {
            return SideState.NONE;
        }
        return SideState.values()[(value >> (side.ordinal() * 2)) & 3];
    }

    private static boolean isWorking(IntSyncValue sides, ForgeDirection side) {
        int value = sides.getIntValue();
        return value >= 0 && (value & HAS_WORKING_BIT) != 0 && ((value >> WORKING_SHIFT) & 7) == side.ordinal();
    }
}
