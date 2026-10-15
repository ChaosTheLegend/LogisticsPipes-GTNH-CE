package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularPanel;

import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.string.StringUtils;

/** Request result popup: the requested or missing resources, with OK and a button that copies them to chat. */
final class RequestTableResultPopup {

    private static int nextId;

    private RequestTableResultPopup() {}

    static void open(ModularPanel parent, EntityPlayer player, String title, Collection<IResource> items) {
        List<String> text = new ArrayList<>();
        text.add(title);
        for (IResource item : items) {
            text.add(item.getDisplayText(IResource.ColorCode.NONE));
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int widest = 0;
        for (String line : text) widest = Math.max(widest, font.getStringWidth(line));
        int width = Math.max(Math.min(widest + 20, 400), 120);
        int height = text.size() * 10 + 40;
        IPanelHandler handler = IPanelHandler.simple(parent, (panel, viewer) -> {
            RequestTablePopupPanel popup = new RequestTablePopupPanel(
                    "request_table_result_" + nextId++,
                    width,
                    height);
            popup.onEnter(popup::closeIfOpen);
            popup.child(new RequestTablePaintWidget((w, h) -> {
                try (var ignored = RequestTableRender.guiState()) {
                    for (int i = 0; i < text.size(); i++) {
                        String line = StringUtils.getCuttedString(text.get(i), width - 10, font);
                        font.drawString(line, (width - font.getStringWidth(line)) / 2, 10 + i * 10, 0x404040);
                    }
                }
            }).pos(0, 0).size(width, height));
            boolean[] logged = { false };
            popup.child(
                    RequestTableMui.textButton(() -> "OK", () -> true).pos(width / 2 - 55, height - 25).size(50, 20)
                            .onLeftClick(popup::closeIfOpen));
            popup.child(
                    RequestTableMui.textButton(() -> "Log", () -> !logged[0]).pos(width / 2 + 5, height - 25)
                            .size(50, 20).onLeftClick(() -> {
                                for (String line : text) player.addChatMessage(new ChatComponentText(line));
                                logged[0] = true;
                            }));
            return popup;
        }, true);
        handler.openPanel();
    }
}
