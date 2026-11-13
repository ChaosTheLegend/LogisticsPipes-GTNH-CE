/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.pipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.LPConstants;
import logisticspipes.LogisticsPipes;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.config.Configs;
import logisticspipes.gui.GuiChassiPipe;
import logisticspipes.gui.hud.HudChassisPipe;
import logisticspipes.gui.modularUI.ChassisGui;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.interfaces.IBufferItems;
import logisticspipes.interfaces.IFluidContainerReceiver;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.interfaces.ISendQueueContentRecieiver;
import logisticspipes.interfaces.ISendRoutedItem;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ChassiTransportLayer;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.modules.ChassiModule;
import logisticspipes.modules.ModuleFluidProvider;
import logisticspipes.modules.ModuleProvider;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.pipe.ChassiOrientationPacket;
import logisticspipes.network.packets.pipe.ChassiePipeModuleContent;
import logisticspipes.network.packets.pipe.RequestChassiOrientationPacket;
import logisticspipes.network.packets.pipe.SendQueueContent;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.modulehost.FluidTarget;
import logisticspipes.pipes.modulehost.IHostResourceManager;
import logisticspipes.pipes.modulehost.IModuleHostPipe;
import logisticspipes.pipes.modulehost.ModuleHost;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.ticks.HudUpdateTick;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.IHavePriority;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;

@CCType(name = "LogisticsChassiePipe")
public abstract class PipeLogisticsChassi extends CoreRoutedPipe
        implements ICraftItems, IBufferItems, ISimpleInventoryEventHandler, ISendRoutedItem, IProvideItems,
        IHeadUpDisplayRendererProvider, ISendQueueContentRecieiver, IMUICompatiblePipeV2, IFluidModuleHost, IFluidSink,
        IProvideFluids, IFluidContainerReceiver, IModuleHostPipe, IHavePriority {

    private final ChassiModule _module;
    private boolean switchOrientationOnTick = true;
    private boolean init = false;

    private boolean convertFromMeta = false;

    // HUD
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HudChassisPipe hud;

    public PipeLogisticsChassi(Item item) {
        super(item);
        // the common host of CoreRoutedPipe: it saves, ticks and drops the modules for the chassis too
        moduleHost = ModuleHost.forChassis(this, getChassiSize());
        moduleHost.getModuleInventory().addListener(this);
        _module = new ChassiModule(this, moduleHost);
        hud = new HudChassisPipe(this, _module, moduleHost.getModuleInventory());
        pointedDirection = ForgeDirection.UNKNOWN;
    }

    @Override
    protected List<IInventory> getConnectedRawInventories() {
        if (_cachedAdjacentInventories != null) {
            return _cachedAdjacentInventories;
        }
        List<IInventory> adjacent = new ArrayList<>(1);
        IInventory adjinv = getRealInventory();
        if (adjinv != null) {
            adjacent.add(adjinv);
        }
        _cachedAdjacentInventories = adjacent;
        return _cachedAdjacentInventories;
    }

    public void nextOrientation() {
        boolean found = false;
        ForgeDirection oldOrientation = pointedDirection;
        for (int l = 0; l < 6; ++l) {
            pointedDirection = ForgeDirection.values()[(pointedDirection.ordinal() + 1) % 6];
            if (isValidOrientation(pointedDirection)) {
                found = true;
                break;
            }
        }
        if (!found) {
            pointedDirection = ForgeDirection.UNKNOWN;
        }
        orientationChanged(oldOrientation);
    }

    // the working side is the side the chassis points at

    @Override
    public boolean hasWorkingSide() {
        return true;
    }

    @Override
    public ForgeDirection getWorkingSide() {
        return pointedDirection == null ? ForgeDirection.UNKNOWN : pointedDirection;
    }

    @Override
    public boolean setWorkingSide(ForgeDirection side) {
        if (side == null || side == ForgeDirection.UNKNOWN || !isValidOrientation(side)) {
            return false;
        }
        ForgeDirection oldOrientation = pointedDirection;
        pointedDirection = side;
        orientationChanged(oldOrientation);
        return true;
    }

    private void orientationChanged(ForgeDirection oldOrientation) {
        if (pointedDirection != oldOrientation) {
            clearCache();
            MainProxy.sendPacketToAllWatchingChunk(
                    getX(),
                    getZ(),
                    MainProxy.getDimensionForWorld(getWorld()),
                    PacketHandler.getPacket(ChassiOrientationPacket.class).setDir(pointedDirection).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()));
            refreshRender(true);
        }
    }

    public void setClientOrientation(ForgeDirection dir) {
        if (MainProxy.isClient(getWorld())) {
            pointedDirection = dir;
        }
    }

    protected boolean isValidOrientation(ForgeDirection connection) {
        if (connection == ForgeDirection.UNKNOWN) {
            return false;
        }
        if (getRouter().isRoutedExit(connection)) {
            return false;
        }
        LPPosition pos = new LPPosition(getX(), getY(), getZ());
        pos.moveForward(connection);
        TileEntity tile = pos.getTileEntity(getWorld());

        if (tile == null) {
            return false;
        }
        for (IHostResourceManager manager : moduleHost.getManagers()) {
            if (manager.isValidTarget(tile, connection)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFluidPipeLook() {
        return moduleHost.getFluidManager().hasModules();
    }

    @Override
    public boolean logisitcsIsPipeConnected(TileEntity tile, ForgeDirection dir) {
        return moduleHost.getFluidManager().isValidTarget(tile, dir);
    }

    public IInventory getModuleInventory() {
        return moduleHost.getModuleInventory();
    }

    /**
     * @return true if that module item can be installed in this kind of chassis
     */
    public boolean isModuleAllowed(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemModule;
    }

    public ModuleUpgradeManager getModuleUpgradeManager(int slot) {
        return moduleHost.getUpgradeManager(slot);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_TEXTURE;
    }

    @Override
    public TextureType getRoutedTexture(ForgeDirection connection) {
        if (getRouter().isSubPoweredExit(connection)) {
            return Textures.LOGISTICSPIPE_SUBPOWER_TEXTURE;
        }
        return Textures.LOGISTICSPIPE_CHASSI_ROUTED_TEXTURE;
    }

    @Override
    public TextureType getNonRoutedTexture(ForgeDirection connection) {
        if (connection.equals(pointedDirection)) {
            return Textures.LOGISTICSPIPE_CHASSI_DIRECTION_TEXTURE;
        }
        if (isPowerProvider(connection)) {
            return Textures.LOGISTICSPIPE_POWERED_TEXTURE;
        }
        return Textures.LOGISTICSPIPE_CHASSI_NOTROUTED_TEXTURE;
    }

    @Override
    public void onNeighborBlockChange_Logistics() {
        if (!isValidOrientation(pointedDirection)) {
            if (MainProxy.isServer(getWorld())) {
                nextOrientation();
            }
        }
    }

    @Override
    public void onBlockPlaced() {
        super.onBlockPlaced();
        switchOrientationOnTick = true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        try {
            super.readFromNBT(nbttagcompound);
            pointedDirection = ForgeDirection.values()[nbttagcompound.getInteger("Orientation") % 7];
            if (nbttagcompound.getInteger("Orientation") == 0) {
                convertFromMeta = true;
            }
            switchOrientationOnTick = (pointedDirection == ForgeDirection.UNKNOWN);
        } catch (Exception e) {
            LogisticsPipes.log.error("Failed to read a chassis pipe, its modules or settings may be lost", e);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        if (pointedDirection == null) {
            pointedDirection = ForgeDirection.UNKNOWN;
        }
        nbttagcompound.setInteger("Orientation", pointedDirection.ordinal());
    }

    @Override
    public void onAllowedRemoval() {
        moduleHost.getModuleInventory().removeListener(this);
        super.onAllowedRemoval();
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        moduleHost.itemArrived(item, info);
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        moduleHost.itemLost(item, info);
    }

    @Override
    public int addToBuffer(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        return moduleHost.addToBuffer(item, info);
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        moduleHost.onModuleInventoryChanged();
    }

    @Override
    public void onHostModulesChanged(boolean moduleRemoved) {
        if (moduleRemoved) {
            if (MainProxy.isClient(getWorld())) {
                if (FMLClientHandler.instance().getClient().currentScreen instanceof GuiChassiPipe) {
                    FMLClientHandler.instance().getClient().currentScreen.initGui();
                }
            }
        }
        if (MainProxy.isServer()) {
            if (!localModeWatchers.isEmpty()) {
                MainProxy.sendToPlayerList(
                        PacketHandler.getPacket(ChassiePipeModuleContent.class)
                                .setIdentList(ItemIdentifierStack.getListFromInventory(moduleHost.getModuleInventory()))
                                .setPosX(getX()).setPosY(getY()).setPosZ(getZ()),
                        localModeWatchers);
            }
        }
    }

    @Override
    public void ignoreDisableUpdateEntity() {
        if (switchOrientationOnTick) {
            switchOrientationOnTick = false;
            if (MainProxy.isServer(getWorld())) {
                nextOrientation();
            }
        }
        if (convertFromMeta && getWorld().getBlockMetadata(getX(), getY(), getZ()) != 0) {
            pointedDirection = ForgeDirection.values()[getWorld().getBlockMetadata(getX(), getY(), getZ()) % 6];
            getWorld().setBlockMetadataWithNotify(getX(), getY(), getZ(), 0, 0);
            convertFromMeta = false;
        }
        if (!init) {
            init = true;
            if (MainProxy.isClient(getWorld())) {
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(RequestChassiOrientationPacket.class).setPosX(getX()).setPosY(getY())
                                .setPosZ(getZ()));
            }
        }
    }

    public abstract int getChassiSize();

    @Override
    public final LogisticsModule getLogisticsModule() {
        return _module;
    }

    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new ChassiTransportLayer(this);
        }
        return _transportLayer;
    }

    private boolean tryInsertingModule(EntityPlayer entityplayer) {
        IInventory moduleInventory = moduleHost.getModuleInventory();
        for (int i = 0; i < moduleInventory.getSizeInventory(); i++) {
            ItemStack item = moduleInventory.getStackInSlot(i);
            if (item == null) {
                moduleInventory.setInventorySlotContents(i, entityplayer.getCurrentEquippedItem().splitStack(1));
                InventoryChanged(moduleInventory);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
        if (entityplayer.getCurrentEquippedItem() == null) {
            return false;
        }

        if (!entityplayer.isSneaking() && entityplayer.getCurrentEquippedItem().getItem() == LogisticsPipes.ModuleItem
                && entityplayer.getCurrentEquippedItem().getItemDamage() != ItemModule.BLANK
                && isModuleAllowed(entityplayer.getCurrentEquippedItem())) {
            if (MainProxy.isServer(getWorld())) {
                if (settings == null || settings.openGui) {
                    return tryInsertingModule(entityplayer);
                } else {
                    entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            return true;
        }

        return false;
    }

    /*** IProvideItems ***/
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (tree.getRequestType() instanceof FluidResource) {
            moduleHost.getFluidManager().canProvide(tree, root, filters);
        } else {
            moduleHost.getItemManager().canProvide(tree, root, filters);
        }
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        return moduleHost.getItemManager().fullFill(promise, destination, info);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
        moduleHost.getItemManager().getAllItems(list, filter);
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return hud;
    }

    @Override
    public void startWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStartWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void stopWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStopWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
        hud.stopWatching();
    }

    @Override
    public void playerStartWatching(EntityPlayer player, int mode) {
        if (mode == 1) {
            localModeWatchers.add(player);
            MainProxy
                    .sendPacketToPlayer(
                            PacketHandler.getPacket(ChassiePipeModuleContent.class)
                                    .setIdentList(
                                            ItemIdentifierStack.getListFromInventory(moduleHost.getModuleInventory()))
                                    .setPosX(getX()).setPosY(getY()).setPosZ(getZ()),
                            player);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(SendQueueContent.class)
                            .setIdentList(ItemIdentifierStack.getListSendQueue(_sendQueue)).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
        } else {
            super.playerStartWatching(player, mode);
        }
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
        super.playerStopWatching(player, mode);
        localModeWatchers.remove(player);
    }

    public void handleModuleItemIdentifierList(Collection<ItemIdentifierStack> _allItems) {
        moduleHost.getModuleInventory().handleItemIdentifierList(_allItems);
    }

    @Override
    public int sendQueueChanged(boolean force) {
        if (MainProxy.isServer(getWorld())) {
            if (Configs.MULTI_THREAD_NUMBER > 0 && !force) {
                HudUpdateTick.add(getRouter());
            } else {
                if (localModeWatchers != null && localModeWatchers.size() > 0) {
                    LinkedList<ItemIdentifierStack> items = ItemIdentifierStack.getListSendQueue(_sendQueue);
                    MainProxy.sendToPlayerList(
                            PacketHandler.getPacket(SendQueueContent.class).setIdentList(items).setPosX(getX())
                                    .setPosY(getY()).setPosZ(getZ()),
                            localModeWatchers);
                    return items.size();
                }
            }
        }
        return 0;
    }

    @Override
    public void handleSendQueueItemIdentifierList(Collection<ItemIdentifierStack> _allItems) {
        displayList.clear();
        displayList.addAll(_allItems);
    }

    public ChassiModule getModules() {
        return _module;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ChassisGui(this);
    }

    /*** interests: a module only counts while the chassis points at something its manager works on ***/

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> interests = new TreeSet<>();
        moduleHost.addSpecificInterests(interests);
        return interests;
    }

    @Override
    public boolean hasGenericInterests() {
        return moduleHost.hasGenericInterests();
    }

    @Override
    public CategoryInterests getCategoryInterests() {
        return moduleHost.getCategoryInterests();
    }

    @CCCommand(description = "Returns the LogisticsModule for the given slot number starting by 1")
    public LogisticsModule getModuleInSlot(Double i) {
        return _module.getSubModule((int) (i - 1));
    }

    @CCCommand(description = "Returns the size of this Chassie pipe")
    public Integer getChassieSize() {
        return getChassiSize();
    }

    public abstract ResourceLocation getChassiGUITexture();

    /*** arriving items ***/

    /**
     * @return the manager an item sent to this chassis is for
     */
    public IHostResourceManager getManagerFor(ItemIdentifierStack stack, IAdditionalTargetInformation info) {
        return moduleHost.getManagerFor(stack, info);
    }

    @Override
    public boolean endReached(LPTravelingItemServer arrivingItem, TileEntity tile) {
        return moduleHost.takeArrival(arrivingItem);
    }

    /*** IFluidModuleHost ***/

    @Override
    public List<FluidTarget> getFluidTargets(int moduleSlot) {
        return moduleHost.getFluidManager().getTargets(moduleSlot);
    }

    /*** IModuleHostPipe ***/

    @Override
    public List<ForgeDirection> getTargetDirections() {
        if (pointedDirection == null || pointedDirection == ForgeDirection.UNKNOWN) {
            return Collections.emptyList();
        }
        return Collections.singletonList(pointedDirection);
    }

    @Override
    public int getReservedFluid(FluidIdentifier fluid) {
        return moduleHost.getFluidManager().getReservedFluid(fluid);
    }

    /*** IFluidSink ***/

    @Override
    public int sinkAmount(FluidStack stack) {
        return moduleHost.getFluidManager().sinkAmount(stack);
    }

    @Override
    public FixedPriority getFluidSinkPriority(FluidStack stack) {
        return moduleHost.getFluidManager().getFluidSinkPriority(stack);
    }

    @Override
    public int getFluidSinkCustomPriority(FluidStack stack) {
        return moduleHost.getFluidManager().getFluidSinkCustomPriority(stack);
    }

    /*** IProvideFluids ***/

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        return moduleHost.getFluidManager().getAvailableFluids();
    }

    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        return moduleHost.getFluidManager().fullFill(promise, destination, type, info);
    }

    /*** ICraftItems ***/

    @Override
    public void registerExtras(IPromise promise) {
        moduleHost.getItemManager().registerExtras(promise);
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        return moduleHost.getItemManager().addCrafting(toCraft);
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        return moduleHost.getItemManager().getConfiguredCraftResults();
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        return moduleHost.getItemManager().canCraft(toCraft);
    }

    @Override
    public ISlotUpgradeManager getUpgradeManager(ModulePositionType slot, int positionInt) {
        if (slot != ModulePositionType.SLOT || positionInt >= moduleHost.getSlotCount()) {
            if (LPConstants.DEBUG) {
                new UnsupportedOperationException(
                        "Position info arn't for a chassi pipe. (" + slot + "/" + positionInt + ")").printStackTrace();
            }
            return super.getUpgradeManager(slot, positionInt);
        }
        return moduleHost.getUpgradeManager(positionInt);
    }

    @Override
    public int getTodo() {
        // TODO Auto-generated method stub
        // probably not needed, the chasi order manager handles the count, would need to store origin to specifically
        // know this.
        return 0;
    }

    /**
     * The highest priority of the chassis's providers (item and fluid): which provider a request asks first.
     */
    @Override
    public int getPriority() {
        int priority = Integer.MIN_VALUE;
        for (int i = 0; i < getChassiSize(); i++) {
            LogisticsModule module = getModules().getSubModule(i);
            if (module instanceof ModuleProvider || module instanceof ModuleFluidProvider) {
                priority = Math.max(priority, module.getPriority());
            }
        }
        return priority == Integer.MIN_VALUE ? 0 : priority;
    }
}
