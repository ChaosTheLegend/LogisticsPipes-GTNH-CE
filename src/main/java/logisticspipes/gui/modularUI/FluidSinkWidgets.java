package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import logisticspipes.interfaces.IFluidSinkSettings;
import logisticspipes.utils.FluidFilter;

/**
 * The widgets the pages of the fluid sinks share.
 */
public final class FluidSinkWidgets {

    private FluidSinkWidgets() {}

    /**
     * @param syncManager the manager of a page that is built more than once, like the page of a module in a chassis, or
     *                    null
     * @param id          the id of the page, the sync handlers are registered under it
     * @return the slots of the filter in a row
     */
    public static Flow filterRow(FluidFilter filter, PanelSyncManager syncManager, String id) {
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < FluidFilter.SLOTS; i++) {
            int slot = i;
            FluidSlotSyncHandler handler = syncManager != null ? syncManager.getOrCreateSyncHandler(
                    id + "_filter",
                    slot,
                    FluidSlotSyncHandler.class,
                    () -> newFilterSlot(filter, slot)) : newFilterSlot(filter, slot);
            row.child(new FluidSlot().syncHandler(handler));
        }
        return row;
    }

    private static FluidSlotSyncHandler newFilterSlot(FluidFilter filter, int slot) {
        return new FluidSlotSyncHandler(filter.getTank(slot)).phantom(true).controlsAmount(false);
    }

    /**
     * @return the page of a fluid sink: the filter, the button that reads it from the tank and the default route
     *         switch. The parameters are the ones of {@link #filterRow}.
     */
    public static Flow sinkPage(IFluidSinkSettings settings, PanelSyncManager syncManager, String id) {
        BooleanSyncValue defaultRouteSync = syncManager != null ? syncManager.getOrCreateSyncHandler(
                id + "_default_route",
                BooleanSyncValue.class,
                () -> newDefaultRouteValue(settings)) : newDefaultRouteValue(settings);

        InteractionSyncHandler importSync = syncManager != null ? syncManager
                .getOrCreateSyncHandler(id + "_import", 0, InteractionSyncHandler.class, InteractionSyncHandler::new)
                : new InteractionSyncHandler();
        importSync.setOnMousePressed(mouse -> {
            if (mouse.mouseButton != 0) return;
            settings.importFromTank();
        });

        Flow defaultRoute = new Flow(GuiAxis.X).coverChildren().childPadding(4)
                .child(IKey.lang("gui.module.ModuleFluidSink.defaultRoute").asWidget()).child(
                        new CycleButtonWidget().width(26).height(16).value(defaultRouteSync).overlay(
                                IKey.lang(
                                        () -> settings.isDefaultRoute() ? "gui.module.ModuleFluidSink.on"
                                                : "gui.module.ModuleFluidSink.off")));

        Flow controls = new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f)
                .child(
                        new ButtonWidget<>().syncHandler(importSync)
                                .overlay(IKey.lang("gui.module.ModuleFluidSink.import")).width(40).height(16))
                .child(new Flow(GuiAxis.X).expanded()).child(defaultRoute);

        return new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.START).child(IKey.lang("gui.module.ModuleFluidSink").asWidget())
                .child(filterRow(settings.getFilter(), syncManager, id)).child(controls);
    }

    private static BooleanSyncValue newDefaultRouteValue(IFluidSinkSettings settings) {
        return new BooleanSyncValue(settings::isDefaultRoute, settings::setDefaultRoute).allowC2S();
    }
}
