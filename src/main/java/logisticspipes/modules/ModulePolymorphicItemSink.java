package logisticspipes.modules;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleInfoMuiDynamic;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.modulehost.ModuleSlotTargetInformation;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;

public class ModulePolymorphicItemSink extends LogisticsModule implements IMUICompatibleModule, IPrioritizedModule {

    public ModulePolymorphicItemSink() {}

    private SinkReply _sinkReply;

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        _sinkReply = new SinkReply(
                FixedPriority.ItemSink,
                0,
                true,
                false,
                3,
                0,
                new ModuleSlotTargetInformation(getPositionInt()));
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        SinkReply reply = prioritized(_sinkReply);
        if (bestPriority > reply.fixedPriority.ordinal()
                || (bestPriority == reply.fixedPriority.ordinal() && bestCustomPriority >= reply.customPriority)) {
            return null;
        }
        IInventoryUtil targetInventory = _service.getSneakyInventory(false, slot, positionInt);
        if (targetInventory == null) {
            return null;
        }

        if (!targetInventory.containsUndamagedItem(item.getUndamaged())) {
            return null;
        }

        if (_service.canUseEnergy(3)) {
            return reply;
        }
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        readPriority(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        writePriority(nbttagcompound);
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {}

    @Override
    public final int getX() {
        return _service.getX();
    }

    @Override
    public final int getY() {
        return _service.getY();
    }

    @Override
    public final int getZ() {
        return _service.getZ();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    // TODO: SINK UNDAMAGED MATCH CORRECTLY!

    @Override
    public List<ItemIdentifier> getSpecificInterests() {
        return null;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return true; // by definition :)
    }

    @Override
    public boolean interestedInUndamagedID() {
        return true;
    }

    @Override
    public boolean recievePassive() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModulePolymorphicItemSink");
    }

    // nothing to configure: a window that says what the module does and holds its upgrade slots in hand

    @Override
    public LogisticsModularUI getHandGui() {
        return ModuleInfoMuiDynamic.of(this, "");
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return ModuleInfoMuiDynamic.of(this, "");
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return ModuleInfoMuiDynamic.of(this, prefix);
    }
}
