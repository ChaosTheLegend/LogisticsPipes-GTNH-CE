/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.routing;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IProvide;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import lombok.Getter;

public class FluidLogisticsPromise implements IPromise {

    @Getter
    private final FluidIdentifier liquid;
    @Getter
    private int amount;
    @Getter
    private final IProvideFluids sender;
    @Getter
    private final ResourceType type;

    public FluidLogisticsPromise(FluidIdentifier liquid, int amount, IProvideFluids sender, ResourceType type) {
        this.liquid = liquid;
        this.amount = amount;
        this.sender = sender;
        this.type = type;
    }

    @Override
    public FluidLogisticsPromise copy() {
        return new FluidLogisticsPromise(liquid, amount, sender, type);
    }

    public FluidLogisticsPromise copyWithAmount(int amount) {
        return new FluidLogisticsPromise(liquid, amount, sender, type);
    }

    protected void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public boolean matches(IResource requestType) {
        if (requestType instanceof FluidResource) {
            FluidResource fluid = (FluidResource) requestType;
            return fluid.getFluid().equals(liquid);
        }
        return false;
    }

    @Override
    public IExtraPromise split(int more) {
        amount -= more;
        return new FluidExtraPromise(liquid, more, sender, false);
    }

    @Override
    public IProvide getProvider() {
        return sender;
    }

    @Override
    public ItemIdentifier getItemType() {
        return liquid.getItemIdentifier();
    }

    @Override
    public IOrderInfoProvider fullFill(IResource requestType, IAdditionalTargetInformation info) {
        return sender.fullFill(this, ((FluidResource) requestType).getTarget(), type, info);
    }
}
