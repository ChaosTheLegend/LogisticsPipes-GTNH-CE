package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.items.ItemModule;
import logisticspipes.migration.LegacyHelper;
import logisticspipes.modules.ModuleFluidTankSupplier;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Keeps every tank next to it at a set amount of one fluid by requesting it from the network. Its built-in fluid tank
 * supplier module, the same one that goes into a chassis, does the requesting and keeps the settings; the module host
 * takes the delivered fluid.
 */
public class PipeFluidSupplierMk2 extends FluidRoutedPipe implements IMUICompatiblePipeV2, IRequireReliableTransport {

    public PipeFluidSupplierMk2(Item item) {
        super(item);
        hostModule(ItemModule.FLUID_SUPPLIER);
    }

    private ModuleFluidTankSupplier supplier() {
        return (ModuleFluidTankSupplier) getLogisticsModule();
    }

    /**
     * Requested fluid arrived: the module stops counting it as requested.
     */
    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        moduleHost.itemArrived(item, info);
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        moduleHost.itemLost(item, info);
    }

    /* TRIGGER INTERFACE */
    public boolean isRequestFailed() {
        return supplier().isRequestFailed();
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Fast;
    }

    /**
     * An active destination, as before the module did the work.
     */
    @Override
    public boolean hasGenericInterests() {
        return true;
    }

    @Override
    public boolean canInsertFromSideToTanks() {
        return true;
    }

    @Override
    public boolean canInsertToTanks() {
        return true;
    }

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    /**
     * The module reads its settings from the pipe's tag, where the pipe kept them before ("tank", "amount",
     * "requestpartials", "refillThreshold"). Older formats are converted here.
     */
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        ModuleFluidTankSupplier supplier = supplier();
        if (compound.hasKey("items")) {
            // legacy: the fluid was kept as an item
            LegacyHelper.readItemIdentifierInventoryAndConvertToTank(supplier.phantomTank, compound, "");
        }
        if (compound.hasKey("_bucketMinimum")) {
            // legacy: the refill threshold was one of four steps
            byte bucketMinimum = compound.getByte("_bucketMinimum");
            if (bucketMinimum == 0) supplier.refillThreshold = 0;
            if (bucketMinimum == 1) supplier.refillThreshold = 1000;
            if (bucketMinimum == 2) supplier.refillThreshold = 2000;
            if (bucketMinimum == 3) supplier.refillThreshold = 5000;
        }
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_LIQUIDSUPPLIER_MK2_TEXTURE;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        // the window of its fluid supplier module, with the module's upgrades and the settings window
        return PipeGuiFactory.fromModule(this, (IMUICompatibleModule) getLogisticsModule());
    }
}
