package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.crafting.IPatternSatellitePipe;
import logisticspipes.crafting.SatelliteInventoryClearer;
import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.ISatellitePipe;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeSatelliteMui extends LogisticsPipeMUI {

    private static final int NAME_MAX_LENGTH = 64;

    public PipeSatelliteMui(CoreRoutedPipe pipe) {
        super(pipe);
    }

    @Override
    public String getId() {
        return "pipe_satellite";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        // value sync handlers reject client edits unless allowC2S() is set; opening the GUI already passed security
        ISatellitePipe satellitePipe = (ISatellitePipe) pipe;
        IPatternSatellitePipe patternSatellite = pipe instanceof IPatternSatellitePipe p ? p : null;

        Flow column = new Column().coverChildren().top(4).childPadding(4).leftRel(Alignment.Center.x)
                .anchorLeft(Alignment.Center.x).topRel(Alignment.Center.y).anchorTop(Alignment.Center.y)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        if (patternSatellite != null) {
            column.child(new TextWidget<>(IKey.lang("gui.satellite.PatternSatellite")));
        }

        column.child(new TextWidget<>(IKey.lang("gui.satellite.SatelliteID")))
                .child(
                        new TextFieldWidget().width(80).setNumbers(0, Integer.MAX_VALUE).value(
                                SyncHandlers.intNumber(satellitePipe::getSatelliteId, satellitePipe::setSatelliteId)
                                        .allowC2S()))
                .child(
                        new ButtonWidget<>().width(80).overlay(IKey.lang("Next free"))
                                .syncHandler(new InteractionSyncHandler().setOnMousePressed(i -> {
                                    if (i.mouseButton != 0) return;
                                    satellitePipe.setNextFreeId();
                                })));

        if (patternSatellite != null) {
            // the server may add a suffix to keep the name unique in the network; the field syncs the result back
            column.child(new TextWidget<>(IKey.lang("gui.satellite.Name"))).child(
                    new TextFieldWidget().width(104).setMaxLength(NAME_MAX_LENGTH).value(
                            SyncHandlers.string(patternSatellite::getSatelliteName, patternSatellite::setSatelliteName)
                                    .allowC2S()));
        }

        column.child(
                new ButtonWidget<>().width(104).overlay(IKey.lang("gui.satellite.ClearInventory")).syncHandler(
                        new InteractionSyncHandler().setOnMousePressed(
                                i -> { if (i.mouseButton == 0) SatelliteInventoryClearer.clearInventory(pipe); })));
        widget.child(column);
        return widget;
    }

    @Override
    public int getWidth() {
        return pipe instanceof IPatternSatellitePipe ? 124 : 120;
    }

    @Override
    public int getHeight() {
        return pipe instanceof IPatternSatellitePipe ? 148 : 104;
    }
}
