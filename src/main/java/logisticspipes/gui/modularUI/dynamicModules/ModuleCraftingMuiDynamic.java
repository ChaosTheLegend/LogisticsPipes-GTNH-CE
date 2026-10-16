package logisticspipes.gui.modularUI.dynamicModules;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleCrafter;

public class ModuleCraftingMuiDynamic extends GenericModuleMUI<ModuleCrafter> {

    private final IItemHandlerModifiable craftingInventory;

    public ModuleCraftingMuiDynamic(ModuleCrafter module) {
        super(module);
        craftingInventory = new InvWrapper(module.getDummyInventory());
    }

    @Override
    public String getId() {
        return "module_crafting";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).coverChildrenHeight().fullWidth().childPadding(4)
                        // Input slots label
                        .child(new TextWidget<>("Inputs").left(4).height(10))
                        // 9 input phantom slots (slots 0-8)
                        .child(buildInputSlots(syncManager))
                        // Output slot row
                        .child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f).childPadding(4)
                                        .child(
                                                new TextWidget<>("Output").height(10).topRel(Alignment.END.y)
                                                        .anchorTop(Alignment.END.y))
                                        .child(buildSingleSlot(syncManager, 9, "_output")))
                        // Satellite row: < [id] >
                        .child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f).childPadding(4)
                                        .child(
                                                new TextWidget<>("Satellite:").height(10).topRel(Alignment.CENTER.y)
                                                        .anchorTop(Alignment.CENTER.y))
                                        .child(
                                                new ButtonWidget<>().width(14).height(14).overlay(IKey.str("<"))
                                                        .onMousePressed(btn -> {
                                                            if (btn != 0) return false;
                                                            module.setPrevSatellite(Minecraft.getMinecraft().thePlayer);
                                                            return true;
                                                        }))
                                        .child(
                                                IKey.lang(
                                                        () -> module.satelliteId == 0 ? "Off"
                                                                : String.valueOf(module.satelliteId))
                                                        .asWidget().width(30).height(14).leftRel(Alignment.Center.x)
                                                        .anchorLeft(Alignment.Center.x).topRel(Alignment.Center.y)
                                                        .anchorTop(Alignment.Center.y))
                                        .child(
                                                new ButtonWidget<>().width(14).height(14).overlay(IKey.str(">"))
                                                        .onMousePressed(btn -> {
                                                            if (btn != 0) return false;
                                                            module.setNextSatellite(Minecraft.getMinecraft().thePlayer);
                                                            return true;
                                                        })))
                        // Priority row: v [priority] ^
                        .child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f).childPadding(4)
                                        .child(
                                                new TextWidget<>("Priority:").height(10).topRel(Alignment.CENTER.y)
                                                        .anchorTop(Alignment.CENTER.y))
                                        .child(
                                                new ButtonWidget<>().width(14).height(14).overlay(IKey.str("v"))
                                                        .onMousePressed(btn -> {
                                                            if (btn != 0) return false;
                                                            module.priorityDown(Minecraft.getMinecraft().thePlayer);
                                                            return true;
                                                        }))
                                        .child(
                                                IKey.lang(() -> String.valueOf(module.priority)).asWidget().width(30)
                                                        .height(14).leftRel(Alignment.Center.x)
                                                        .anchorLeft(Alignment.Center.x).topRel(Alignment.Center.y)
                                                        .anchorTop(Alignment.Center.y))
                                        .child(
                                                new ButtonWidget<>().width(14).height(14).overlay(IKey.str("^"))
                                                        .onMousePressed(btn -> {
                                                            if (btn != 0) return false;
                                                            module.priorityUp(Minecraft.getMinecraft().thePlayer);
                                                            return true;
                                                        })))
                        // Action buttons row
                        .child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f).childPadding(4)
                                        .child(
                                                new ButtonWidget<>().width(60).height(14).overlay(IKey.str("Import"))
                                                        .onMousePressed(btn -> {
                                                            if (btn != 0) return false;
                                                            module.importFromCraftingTable(
                                                                    Minecraft.getMinecraft().thePlayer);
                                                            return true;
                                                        }))
                                        .child(
                                                new ButtonWidget<>().width(60).height(14).overlay(IKey.str("Open"))
                                                        .onMousePressed(btn -> {
                                                            if (btn != 0) return false;
                                                            module.openAttachedGui(Minecraft.getMinecraft().thePlayer);
                                                            return true;
                                                        }))));

        return widget;
    }

    private Flow buildInputSlots(PanelSyncManager syncManager) {
        String id = getFullId();
        Flow col = Flow.col().coverChildren().leftRel(Alignment.TopCenter.x).anchorLeft(Alignment.TopCenter.x)
                .topRel(Alignment.TopCenter.y).anchorTop(Alignment.TopCenter.y);
        for (int row = 0; row < 3; row++) {
            Flow rowWidget = Flow.row().coverChildren();
            for (int col2 = 0; col2 < 3; col2++) {
                int slotIndex = row * 3 + col2;
                PhantomItemSlot slotWidget = new PhantomItemSlot();
                if (syncManager != null) {
                    PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(
                            id + "_input",
                            slotIndex,
                            PhantomItemSlotSH.class,
                            () -> new PhantomItemSlotSH(new ModularSlot(craftingInventory, slotIndex)));
                    slotWidget.syncHandler(slotSH);
                } else {
                    slotWidget.slot(craftingInventory, slotIndex);
                }
                rowWidget.child(slotWidget);
            }
            col.child(rowWidget);
        }
        return col;
    }

    private PhantomItemSlot buildSingleSlot(PanelSyncManager syncManager, int slotIndex, String suffix) {
        String id = getFullId();
        PhantomItemSlot slotWidget = new PhantomItemSlot();
        if (syncManager != null) {
            PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(
                    id + suffix,
                    slotIndex,
                    PhantomItemSlotSH.class,
                    () -> new PhantomItemSlotSH(new ModularSlot(craftingInventory, slotIndex)));
            slotWidget.syncHandler(slotSH);
        } else {
            slotWidget.slot(craftingInventory, slotIndex);
        }
        return slotWidget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 260;
    }
}
