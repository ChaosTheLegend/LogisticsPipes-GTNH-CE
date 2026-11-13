package logisticspipes.pipes.basic;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The RF and EU buffers of a pipe. They fed the power supplier upgrades, which are deprecated and deleted on load, so
 * the buffers are only kept with the pipe.
 */
public class PowerSupplierHandler {

    private float internal_RF_Buffer = 0F;
    private float internal_IC2_Buffer = 0F;

    public PowerSupplierHandler(CoreRoutedPipe pipe) {}

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        if (internal_RF_Buffer > 0) {
            nbttagcompound.setFloat("bufferRF", internal_RF_Buffer);
        }
        if (internal_IC2_Buffer > 0) {
            nbttagcompound.setFloat("bufferEU", internal_IC2_Buffer);
        }
    }

    public void readFromNBT(NBTTagCompound nbttagcompound) {
        internal_RF_Buffer = nbttagcompound.getFloat("bufferRF");
        internal_IC2_Buffer = nbttagcompound.getFloat("bufferEU");
    }

    public void addRFPower(float toSend) {
        internal_RF_Buffer += toSend;
    }

    public void addIC2Power(float toSend) {
        internal_IC2_Buffer += toSend;
    }
}
