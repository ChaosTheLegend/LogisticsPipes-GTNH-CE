package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import logisticspipes.crafting.requesttable.gui.upgrade.RequestTableUpgradeBoard;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeSession;

/** The pannable, zoomable upgrade motherboard. Clicking a socket selects that upgrade. */
public class RequestTableUpgradeBoardWidget extends Widget<RequestTableUpgradeBoardWidget> implements Interactable {

    private final RequestTableMui gui;
    private RequestTableUpgradeBoard board;
    // Tooltips are built outside of the draw pass, where the mouse is not widget-local.
    private int hoverX = -1;
    private int hoverY = -1;

    public RequestTableUpgradeBoardWidget(RequestTableMui gui) {
        this.gui = gui;
        tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            if (board == null) return;
            List<String> lines = board.tooltip(hoverX, hoverY, upgrades());
            if (lines != null) tooltip.addStringLines(lines);
        });
    }

    private RequestTableUpgradeBoard board() {
        if (board == null) board = new RequestTableUpgradeBoard();
        board.setArea(0, 0, getArea().width, getArea().height);
        return board;
    }

    private RequestTableUpgradeSession upgrades() {
        return gui.getActions().getUpgrades();
    }

    private int mouseX() {
        return getContext().getMouseX();
    }

    private int mouseY() {
        return getContext().getMouseY();
    }

    public void centerBoard() {
        board().center();
    }

    public void changeZoom(boolean increase) {
        board().changeZoom(increase);
    }

    public void showAll() {
        board().showAll();
    }

    public void cancelDrag() {
        if (board != null) board.cancelDrag();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        return board().startDrag(mouseX(), mouseY(), mouseButton) ? Result.SUCCESS : Result.IGNORE;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        RequestTableUpgradeBoard board = board();
        if (board.isDragging() && mouseButton == board.getDragButton()) board.drag(mouseX(), mouseY());
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        RequestTableUpgradeBoard board = board();
        if (!board.isDragging()) return false;
        board.release(mouseX(), mouseY(), mouseButton, (branch, tier) -> {
            Interactable.playButtonClickSound();
            gui.getActions().selectUpgrade(branch, tier);
        });
        return true;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        board().scroll(scrollDirection == UpOrDown.UP ? 1 : -1, Interactable.hasShiftDown());
        return true;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        hoverX = context.getMouseX();
        hoverY = context.getMouseY();
        RequestTableUpgradeBoard board = board();
        RequestTableUpgradeSession upgrades = upgrades();
        if (upgrades.isReady()) board.initializeView(upgrades.getSelectedBranch(), upgrades.getSelectedTier());
        board.updateProgress(upgrades);
        board.draw(Minecraft.getMinecraft(), upgrades, context.getMouseX(), context.getMouseY());
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
