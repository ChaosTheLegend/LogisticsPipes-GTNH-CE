package logisticspipes.logisticspipes;

import logisticspipes.pipes.PipeLogisticsFluidChassi;

public class FluidChassiTransportLayer extends ChassiTransportLayer {

    public FluidChassiTransportLayer(PipeLogisticsFluidChassi chassiPipe) {
        super(chassiPipe);
    }

    @Override
    public boolean stillWantItem(IRoutedItem item) {
        // fluid is not checked against the item sinks: what the tank can't take is sent on when it arrives
        if (item.getItemIdentifierStack() != null && item.getItemIdentifierStack().getItem().isFluidContainer()) {
            return true;
        }
        return super.stillWantItem(item);
    }
}
