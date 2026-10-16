package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.jetbrains.annotations.NotNull;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidContainerSupplierMuiDynamic;
import logisticspipes.interfaces.IChassiItemReceiver;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidContainerUtil;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidSendUtil;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Keeps a set number of filled containers in the inventory: it requests the empty containers and the fluid from the
 * network, fills the containers itself and puts them into the inventory. Nothing has to craft the filled containers.
 * <p>
 * What arrives for it is kept in the module until it is filled, also when the module is taken out of its chassis.
 */
public class ModuleFluidContainerSupplier extends LogisticsGuiModule
        implements IRequestItems, IRequestFluid, IRequireReliableTransport, IChassiItemReceiver, IMUICompatibleModule {

    private static final int TICKS_PER_FILL = 10;
    private static final int TICKS_PER_REQUEST = 100;
    private static final int ENERGY_PER_REQUEST = 10;
    private static final int FLUID_SEND_CHUNK = 5000;

    // the fluid to fill with, a phantom
    public final FluidTank phantomTank = new FluidTank(1);
    // the empty container to fill, one phantom item
    private final ItemIdentifierInventory containerInventory = new ItemIdentifierInventory(1, "Container", 1);
    // how many filled containers the inventory is to hold
    public int amount = 0;

    // what arrived and wasn't filled yet
    private final Map<ItemIdentifier, Integer> itemBuffer = new HashMap<>();
    private final Map<FluidIdentifier, Integer> fluidBuffer = new HashMap<>();

    // requested and not arrived yet
    private final Map<ItemIdentifier, Integer> requestedItems = new HashMap<>();
    private final Map<FluidIdentifier, Integer> requestedFluids = new HashMap<>();
    private boolean lastRequestFailed;

    // the settings the recipe was worked out for
    private ItemIdentifier cachedContainerSetting;
    private FluidIdentifier cachedFluidSetting;
    private Recipe recipe;

    public ItemIdentifierInventory getContainerInventory() {
        return containerInventory;
    }

    public boolean isRequestFailed() {
        return lastRequestFailed;
    }

    /**
     * @return the filled containers that can be made with what is buffered
     */
    private int getReadyContainers() {
        Recipe current = getRecipe();
        if (current == null) {
            return 0;
        }
        return Math.min(
                getBuffered(itemBuffer, current.container),
                getBuffered(fluidBuffer, current.fluid) / current.capacity);
    }

    /**
     * @return how the set container is filled with the set fluid, or null if they don't go together
     */
    private Recipe getRecipe() {
        ItemIdentifierStack containerStack = containerInventory.getIDStackInSlot(0);
        ItemIdentifier containerSetting = containerStack == null ? null : containerStack.getItem();
        FluidIdentifier fluidSetting = phantomTank.getFluid() == null ? null
                : FluidIdentifier.get(phantomTank.getFluid());
        if (containerSetting != cachedContainerSetting || fluidSetting != cachedFluidSetting) {
            cachedContainerSetting = containerSetting;
            cachedFluidSetting = fluidSetting;
            recipe = makeRecipe(containerSetting, fluidSetting);
        }
        return recipe;
    }

    private static Recipe makeRecipe(ItemIdentifier containerSetting, FluidIdentifier fluid) {
        if (containerSetting == null || fluid == null) {
            return null;
        }
        ItemStack container = containerSetting.makeNormalStack(1);
        // a filled container stands for its empty form
        if (FluidContainerUtil.getFluid(container) != null) {
            container = FluidContainerUtil.getEmptied(container);
            if (container == null) {
                return null;
            }
        }
        FluidStack full = fluid.makeFluidStack(1);
        int capacity = FluidContainerUtil.getCapacity(container, full);
        if (capacity <= 0) {
            return null;
        }
        full.amount = capacity;
        ItemStack filled = FluidContainerUtil.fill(container, full);
        if (filled == null) {
            return null;
        }
        return new Recipe(ItemIdentifier.get(filled), ItemIdentifier.get(container), fluid, capacity);
    }

    // region requesting

    @Override
    public void tick() {
        if (_service == null || MainProxy.isClient(getWorld())) {
            return;
        }
        if (_service.isNthTick(TICKS_PER_FILL)) {
            fill();
        }
        if (_service.isNthTick(TICKS_PER_REQUEST)) {
            returnLeftovers();
            request();
        }
    }

    private IInventoryUtil getInventory() {
        IInventoryUtil inv = _service.getSneakyInventory(false, slot, positionInt);
        return inv != null ? inv : _service.getPointedInventory(false);
    }

    /**
     * Requests the containers and the fluid that are missing for the set number of filled containers.
     */
    private void request() {
        Recipe current = getRecipe();
        if (current == null || amount <= 0) {
            lastRequestFailed = false;
            return;
        }
        IInventoryUtil inv = getInventory();
        if (inv == null) {
            return;
        }
        int wanted = Math.min(amount - inv.itemCount(current.result), inv.roomForItem(current.result));
        if (wanted <= 0) {
            lastRequestFailed = false;
            return;
        }
        IAdditionalTargetInformation info = new ChassiTargetInformation(getPositionInt());
        boolean failed = false;

        int containers = wanted - getBuffered(itemBuffer, current.container)
                - getBuffered(requestedItems, current.container);
        if (containers > 0 && _service.useEnergy(ENERGY_PER_REQUEST)) {
            int ordered = RequestTree.requestPartial(current.container.makeStack(containers), this, info);
            if (ordered > 0) {
                requestedItems.merge(current.container, ordered, Integer::sum);
            }
            failed = ordered < containers;
        }

        long fluid = (long) wanted * current.capacity - getBuffered(fluidBuffer, current.fluid)
                - getBuffered(requestedFluids, current.fluid);
        if (fluid > 0 && _service.useEnergy(ENERGY_PER_REQUEST)) {
            int toOrder = (int) Math.min(Integer.MAX_VALUE, fluid);
            int ordered = RequestTree.requestFluidPartial(current.fluid, toOrder, this, null, info);
            if (ordered > 0) {
                requestedFluids.merge(current.fluid, ordered, Integer::sum);
            }
            failed |= ordered < toOrder;
        }
        lastRequestFailed = failed;
        if (failed) {
            _service.spawnParticle(Particles.RedParticle, 2);
        }
    }

    private static <T> int getBuffered(Map<T, Integer> buffer, T type) {
        Integer amount = buffer.get(type);
        return amount == null ? 0 : amount;
    }

    private static <T> void take(Map<T, Integer> buffer, T type, int amount) {
        int left = getBuffered(buffer, type) - amount;
        if (left > 0) {
            buffer.put(type, left);
        } else {
            buffer.remove(type);
        }
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        decreaseRequested(item);
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        decreaseRequested(item);
    }

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        decreaseRequested(item);
    }

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer amount) {
        take(requestedFluids, fluid, amount);
    }

    private void decreaseRequested(ItemIdentifierStack item) {
        FluidStack fluid = getFluidOf(item);
        if (fluid != null) {
            take(requestedFluids, FluidIdentifier.get(fluid), fluid.amount);
        } else {
            take(requestedItems, item.getItem(), item.getStackSize());
        }
    }

    /**
     * @return the fluid the item carries through the network, or null if it is an ordinary item
     */
    private static FluidStack getFluidOf(ItemIdentifierStack item) {
        return item.getItem().item instanceof LogisticsFluidContainer
                ? SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item)
                : null;
    }

    // endregion

    // region filling

    /**
     * Keeps what was sent to it: the empty containers and the fluid.
     */
    @Override
    public boolean receiveItem(ItemIdentifierStack stack) {
        if (stack == null || stack.getStackSize() <= 0) {
            return false;
        }
        FluidStack fluid = getFluidOf(stack);
        if (fluid != null) {
            fluidBuffer.merge(FluidIdentifier.get(fluid), fluid.amount, Integer::sum);
        } else {
            itemBuffer.merge(stack.getItem(), stack.getStackSize(), Integer::sum);
        }
        stack.setStackSize(0);
        return true;
    }

    /**
     * Fills what the buffer has containers and fluid for and puts it into the inventory.
     */
    private void fill() {
        Recipe current = getRecipe();
        if (current == null) {
            return;
        }
        int made = Math.min(getReadyContainers(), Math.max(1, current.result.getMaxStackSize()));
        if (made <= 0) {
            return;
        }
        IInventory inventory = _service.getRealInventory();
        ForgeDirection side = _service.inventoryOrientation();
        if (inventory == null || side == null || side == ForgeDirection.UNKNOWN) {
            return;
        }
        ForgeDirection insertion = side.getOpposite();
        ISlotUpgradeManager upgrades = _service.getUpgradeManager(slot, positionInt);
        if (upgrades.hasSneakyUpgrade()) {
            insertion = upgrades.getSneakyOrientation();
        }
        ItemStack added = InventoryHelper.getTransactorFor(inventory, side.getOpposite())
                .add(current.result.makeNormalStack(made), insertion, true);
        if (added == null || added.stackSize <= 0) {
            return;
        }
        take(itemBuffer, current.container, added.stackSize);
        take(fluidBuffer, current.fluid, added.stackSize * current.capacity);
        _service.spawnParticle(Particles.VioletParticle, 1);
    }

    /**
     * Sends back into the network what can't be filled any more: the settings were changed. A bit of fluid that is less
     * than a container stays for the next request.
     */
    private void returnLeftovers() {
        if (itemBuffer.isEmpty() && fluidBuffer.isEmpty()) {
            return;
        }
        Recipe current = getRecipe();
        Iterator<Entry<ItemIdentifier, Integer>> items = itemBuffer.entrySet().iterator();
        while (items.hasNext()) {
            Entry<ItemIdentifier, Integer> entry = items.next();
            if (current != null && entry.getKey() == current.container) {
                continue;
            }
            int sent = sendItemsToNetwork(entry.getKey(), entry.getValue());
            if (sent >= entry.getValue()) {
                items.remove();
            } else {
                entry.setValue(entry.getValue() - sent);
            }
        }
        Iterator<Entry<FluidIdentifier, Integer>> fluids = fluidBuffer.entrySet().iterator();
        while (fluids.hasNext()) {
            Entry<FluidIdentifier, Integer> entry = fluids.next();
            if (current != null && entry.getKey() == current.fluid) {
                continue;
            }
            int sent = sendFluidToNetwork(entry.getKey(), entry.getValue());
            if (sent >= entry.getValue()) {
                fluids.remove();
            } else {
                entry.setValue(entry.getValue() - sent);
            }
        }
    }

    /**
     * @return how many of the items the network took
     */
    private int sendItemsToNetwork(ItemIdentifier type, int count) {
        int sent = 0;
        while (sent < count) {
            ItemStack stack = type.makeNormalStack(Math.min(count - sent, Math.max(1, type.getMaxStackSize())));
            IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stack);
            SimpleServiceLocator.logisticsManager.assignDestinationFor(item, getRouter().getSimpleID(), false);
            if (item.getDestinationUUID() == null) {
                break;
            }
            _service.queueRoutedItem(item, getSendSide());
            sent += stack.stackSize;
        }
        return sent;
    }

    /**
     * @return how much of the fluid the network took
     */
    private int sendFluidToNetwork(FluidIdentifier fluid, int count) {
        int sent = 0;
        while (sent < count) {
            FluidStack toSend = fluid.makeFluidStack(Math.min(count - sent, FLUID_SEND_CHUNK));
            int offered = toSend.amount;
            IRoutedItem item = FluidSendUtil.createItemForSink(toSend, getRouter());
            if (item == null) {
                break;
            }
            _service.queueRoutedItem(item, getSendSide());
            sent += offered - toSend.amount;
        }
        return sent;
    }

    private ForgeDirection getSendSide() {
        ForgeDirection side = _service.inventoryOrientation();
        return side == null || side == ForgeDirection.UNKNOWN ? ForgeDirection.UP : side;
    }

    // endregion

    // region the module

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        return null;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return new ArrayList<>(0);
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleFluidContainerSupplier");
    }

    @Override
    public IRouter getRouter() {
        return _service.getRouter();
    }

    @Override
    public int getID() {
        return getRouter().getSimpleID();
    }

    @Override
    public int compareTo(@NotNull IRequest other) {
        return getID() - other.getID();
    }

    // only a ModularUI
    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return null;
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidContainerSupplierMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidContainerSupplierMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidContainerSupplierMuiDynamic(this, prefix);
    }

    // endregion

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        phantomTank.setFluid(null);
        phantomTank.readFromNBT(tag.getCompoundTag("tank"));
        containerInventory.readFromNBT(tag, "container");
        amount = tag.getInteger("amount");
        itemBuffer.clear();
        NBTTagList items = tag.getTagList("bufferItems", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack != null && stack.getItem() != null) {
                // more than a normal stack may wait here
                itemBuffer.merge(ItemIdentifier.get(stack), entry.getInteger("amount"), Integer::sum);
            }
        }
        fluidBuffer.clear();
        NBTTagList fluids = tag.getTagList("bufferFluids", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fluids.tagCount(); i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluids.getCompoundTagAt(i));
            if (fluid != null && fluid.amount > 0) {
                fluidBuffer.merge(FluidIdentifier.get(fluid), fluid.amount, Integer::sum);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("tank", phantomTank.writeToNBT(new NBTTagCompound()));
        containerInventory.writeToNBT(tag, "container");
        tag.setInteger("amount", amount);
        NBTTagList items = new NBTTagList();
        for (Entry<ItemIdentifier, Integer> entry : itemBuffer.entrySet()) {
            NBTTagCompound item = entry.getKey().makeNormalStack(1).writeToNBT(new NBTTagCompound());
            item.setInteger("amount", entry.getValue());
            items.appendTag(item);
        }
        tag.setTag("bufferItems", items);
        NBTTagList fluids = new NBTTagList();
        for (Entry<FluidIdentifier, Integer> entry : fluidBuffer.entrySet()) {
            fluids.appendTag(entry.getKey().makeFluidStack(entry.getValue()).writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("bufferFluids", fluids);
    }

    /**
     * How a filled container is made.
     */
    private static class Recipe {

        private final ItemIdentifier result;
        private final ItemIdentifier container;
        private final FluidIdentifier fluid;
        private final int capacity;

        private Recipe(ItemIdentifier result, ItemIdentifier container, FluidIdentifier fluid, int capacity) {
            this.result = result;
            this.container = container;
            this.fluid = fluid;
            this.capacity = capacity;
        }
    }
}
