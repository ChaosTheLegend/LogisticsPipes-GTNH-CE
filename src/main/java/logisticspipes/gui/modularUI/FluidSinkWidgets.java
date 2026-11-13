package logisticspipes.gui.modularUI;

import java.util.function.Supplier;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

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
     * @param filter      the filter now: a chassis page rebuilt for another module gets the slot handlers of the old
     *                    one (same keys), so they ask for the filter every time
     * @param syncManager the manager of a page that is built more than once, like the page of a module in a chassis, or
     *                    null
     * @param id          the id of the page, the sync handlers are registered under it
     * @return the slots of the filter in a row
     */
    public static Flow filterRow(Supplier<FluidFilter> filter, PanelSyncManager syncManager, String id) {
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < FluidFilter.SLOTS; i++) {
            int slot = i;
            FluidSlotSyncHandler handler = GenericModuleMUI.register(
                    syncManager,
                    id + "_filter",
                    slot,
                    FluidSlotSyncHandler.class,
                    () -> new FluidSlotSyncHandler(new CurrentFilterTank(filter, slot)).phantom(true)
                            .controlsAmount(false));
            row.child(new FluidSlot().syncHandler(handler));
        }
        return row;
    }

    /**
     * @param settings the settings now, see {@link #filterRow}
     * @return the page of a fluid sink: the filter, the button that reads it from the tank and the default route
     *         switch. The other parameters are the ones of {@link #filterRow}.
     */
    public static Flow sinkPage(Supplier<? extends IFluidSinkSettings> settings, PanelSyncManager syncManager,
            String id) {
        BooleanSyncValue defaultRouteSync = GenericModuleMUI.register(
                syncManager,
                id + "_default_route",
                0,
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(
                        () -> settings.get().isDefaultRoute(),
                        v -> settings.get().setDefaultRoute(v)).allowC2S());

        Flow defaultRoute = new Flow(GuiAxis.X).coverChildren().childPadding(4)
                .child(IKey.lang("gui.module.ModuleFluidSink.defaultRoute").asWidget()).child(
                        new CycleButtonWidget().width(26).height(16).value(defaultRouteSync).overlay(
                                IKey.lang(
                                        () -> settings.get().isDefaultRoute() ? "gui.module.ModuleFluidSink.on"
                                                : "gui.module.ModuleFluidSink.off")));

        Flow controls = new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f)
                .child(importButton(() -> settings.get().importFromTank(), syncManager, id))
                .child(new Flow(GuiAxis.X).expanded()).child(defaultRoute);

        return new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.START).child(IKey.lang("gui.module.ModuleFluidSink").asWidget())
                .child(filterRow(() -> settings.get().getFilter(), syncManager, id)).child(controls);
    }

    /**
     * @param importer sets the filter to the fluids in the tank, it runs on the server. The other parameters are the
     *                 ones of {@link #filterRow}.
     * @return the button that reads the filter from the tank
     */
    public static ButtonWidget<?> importButton(Runnable importer, PanelSyncManager syncManager, String id) {
        InteractionSyncHandler importSync = GenericModuleMUI
                .register(syncManager, id + "_import", 0, InteractionSyncHandler.class, InteractionSyncHandler::new);
        importSync.setOnMousePressed(mouse -> {
            // runs on both sides: only the server imports, the client gets the filter synced
            if (syncManager != null && syncManager.isClient()) return;
            if (mouse.mouseButton != 0) return;
            importer.run();
        });
        return new ButtonWidget<>().syncHandler(importSync).overlay(IKey.lang("gui.module.ModuleFluidSink.import"))
                .width(40).height(16);
    }

    /**
     * One tank of whatever filter is there now.
     */
    private static final class CurrentFilterTank implements IFluidTank {

        private final Supplier<FluidFilter> filter;
        private final int slot;

        CurrentFilterTank(Supplier<FluidFilter> filter, int slot) {
            this.filter = filter;
            this.slot = slot;
        }

        private FluidTank tank() {
            return filter.get().getTank(slot);
        }

        @Override
        public FluidStack getFluid() {
            return tank().getFluid();
        }

        @Override
        public int getFluidAmount() {
            return tank().getFluidAmount();
        }

        @Override
        public int getCapacity() {
            return tank().getCapacity();
        }

        @Override
        public FluidTankInfo getInfo() {
            return tank().getInfo();
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return tank().fill(resource, doFill);
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return tank().drain(maxDrain, doDrain);
        }
    }
}
