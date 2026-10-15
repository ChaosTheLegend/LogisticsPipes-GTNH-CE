package logisticspipes.gui.modularUI.pipes.requesttable;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.widgets.Dialog;

import logisticspipes.compat.ModularUIHelper;

/**
 * Modal popup of the request table. Escape closes only the popup and Enter runs its confirm action, like the legacy
 * popups, instead of closing the whole GUI.
 */
class RequestTablePopupPanel extends Dialog<Void> {

    private Runnable onEnter = () -> {};

    RequestTablePopupPanel(String name, int width, int height) {
        super(name);
        size(width, height).background(ModularUIHelper.BACKGROUND_TEXTURE).disableHoverThemeBackground(true);
    }

    RequestTablePopupPanel onEnter(Runnable onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    @Override
    public boolean onKeyPressed(char typedChar, int keyCode) {
        if (super.onKeyPressed(typedChar, keyCode)) return true;
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE -> closeIfOpen();
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> onEnter.run();
            default -> {
                return false;
            }
        }
        return true;
    }
}
