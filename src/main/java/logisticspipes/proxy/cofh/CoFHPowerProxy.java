package logisticspipes.proxy.cofh;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import logisticspipes.proxy.cofh.subproxies.ICoFHEnergyReceiver;
import logisticspipes.proxy.cofh.subproxies.ICoFHEnergyStorage;
import logisticspipes.proxy.interfaces.ICoFHPowerProxy;
import logisticspipes.proxy.interfaces.ICraftingParts;

public class CoFHPowerProxy implements ICoFHPowerProxy {

    @Override
    public boolean isEnergyReceiver(TileEntity tile) {
        return tile instanceof IEnergyReceiver;
    }

    @Override
    public ICoFHEnergyReceiver getEnergyReceiver(TileEntity tile) {
        final IEnergyReceiver handler = (IEnergyReceiver) tile;
        return new ICoFHEnergyReceiver() {

            @Override
            public int getMaxEnergyStored(ForgeDirection opposite) {
                return handler.getMaxEnergyStored(opposite);
            }

            @Override
            public int getEnergyStored(ForgeDirection opposite) {
                return handler.getEnergyStored(opposite);
            }

            @Override
            public boolean canConnectEnergy(ForgeDirection opposite) {
                return handler.canConnectEnergy(opposite);
            }

            @Override
            public int receiveEnergy(ForgeDirection opposite, int i, boolean b) {
                return handler.receiveEnergy(opposite, i, b);
            }
        };
    }

    @Override
    public void addCraftingRecipes(ICraftingParts parts) {
        // its only recipes made the deprecated RF power supply items
    }

    @Override
    public ICoFHEnergyStorage getEnergyStorage(int i) {
        final EnergyStorage energy = new EnergyStorage(i);
        return new ICoFHEnergyStorage() {

            @Override
            public int extractEnergy(int space, boolean b) {
                return energy.extractEnergy(space, b);
            }

            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return energy.receiveEnergy(maxReceive, simulate);
            }

            @Override
            public int getEnergyStored() {
                return energy.getEnergyStored();
            }

            @Override
            public int getMaxEnergyStored() {
                return energy.getMaxEnergyStored();
            }

            @Override
            public void readFromNBT(NBTTagCompound nbt) {
                energy.readFromNBT(nbt);
            }

            @Override
            public void writeToNBT(NBTTagCompound nbt) {
                energy.writeToNBT(nbt);
            }
        };
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
