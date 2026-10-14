package logisticspipes.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.gui.RequestTableGui;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;

/** Makes the terminal's virtual entries available to NEI's normal item shortcuts and tooltips. */
@SideOnly(Side.CLIENT)
public final class RequestTableNEIHandler implements IContainerObjectHandler, IContainerTooltipHandler {

    @Override
    public void guiTick(GuiContainer gui) {}

    @Override
    public void refresh(GuiContainer gui) {}

    @Override
    public void load(GuiContainer gui) {}

    @Override
    public ItemStack getStackUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
        RequestTableNetworkEntry entry = getEntry(gui, mouseX, mouseY);
        return entry == null ? null : entry.getDisplayStack();
    }

    @Override
    public boolean objectUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
        return getEntry(gui, mouseX, mouseY) != null;
    }

    @Override
    public boolean shouldShowTooltip(GuiContainer gui) {
        return true;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mouseX, int mouseY,
            List<String> tooltip) {
        RequestTableNetworkEntry entry = getEntry(gui, mouseX, mouseY);
        if (stack == null || entry == null) return tooltip;

        List<String> details = new ArrayList<>();
        String unit = entry.fluid() ? " mB" : "";
        details.add("\u00a77Network: " + entry.networkAmount() + unit);
        details.add("\u00a77Internal: " + entry.internalAmount() + unit);
        if (entry.craftable()) details.add("\u00a77Craftable");
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            details.add("\u00a77Total: " + entry.getTotalAmount() + unit);
        }
        tooltip.addAll(Math.min(1, tooltip.size()), details);
        return tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mouseX, int mouseY, Map<String, String> hotkeys) {
        ItemStack stack = getStackUnderMouse(gui, mouseX, mouseY);
        if (stack != null) hotkeys.putAll(ShortcutInputHandler.handleHotkeys(mouseX, mouseY, stack));
        return hotkeys;
    }

    private RequestTableNetworkEntry getEntry(GuiContainer gui, int mouseX, int mouseY) {
        return gui instanceof RequestTableGui table ? table.getNetworkEntryUnderMouse(mouseX, mouseY) : null;
    }
}
