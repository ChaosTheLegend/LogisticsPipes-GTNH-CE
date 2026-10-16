package logisticspipes.crafting.requesttable.gui.upgrade;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.gui.upgrade.RequestTableBoardLayout.Node;

/** Native CPU/controller chip art with a single pulse dot or bubble over the installed controller. */
@SideOnly(Side.CLIENT)
final class RequestTableSpecialChips {

    private static final ResourceLocation CENTRAL = texture("central");
    private static final ResourceLocation MONITORING = texture("monitoring");
    private static final ResourceLocation FLUID = texture("fluid");
    private static final ResourceLocation MONITOR_ACTIVITY = texture("monitoring_activity");
    private static final ResourceLocation FLUID_ACTIVITY = texture("fluid_activity");

    private RequestTableSpecialChips() {
    }

    private static ResourceLocation texture(String name) {
        return RequestTableRender.texture("chips/special/" + name);
    }

    static void draw(Node node) {
        if (node.branch == null) {
            drawLayer(CENTRAL, node, 0, 1);
            return;
        }
        switch (node.branch) {
            case CRAFTING_MONITORING -> {
                drawLayer(MONITORING, node, 0, 1);
                drawLayer(MONITOR_ACTIVITY, node, (int) ((Minecraft.getSystemTime() / 200L) % 8), 8);
            }
            case FLUID_CONTROLLER -> {
                drawLayer(FLUID, node, 0, 1);
                drawLayer(FLUID_ACTIVITY, node, (int) ((Minecraft.getSystemTime() / 240L) % 6), 6);
            }
            default -> throw new IllegalArgumentException("Not a special chip: " + node.branch);
        }
    }

    private static void drawLayer(ResourceLocation texture, Node node, int frame, int frames) {
        drawLayer(texture, node, frame, frames, node.height);
    }

    static void drawAssembly(Node node, int visibleRows) {
        drawLayer(node.branch.isFluid() ? FLUID : MONITORING, node, 0, 1, visibleRows);
    }

    private static void drawLayer(ResourceLocation texture, Node node, int frame, int frames, int visibleRows) {
        RequestTableRender.textureRows(
            texture,
            node.getLeft(),
            node.getTop(),
            node.width,
            node.height,
            visibleRows,
            frame,
            frames);
    }
}
