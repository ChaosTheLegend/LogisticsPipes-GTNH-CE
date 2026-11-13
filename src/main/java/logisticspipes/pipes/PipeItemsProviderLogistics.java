package logisticspipes.pipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;

import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.crafting.IStagedProviderReservation;
import logisticspipes.gui.hud.HUDProvider;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IChestContentReceiver;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.interfaces.IOrderManagerContentReceiver;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ExtractionMode;
import logisticspipes.modules.ModuleProvider;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.ChestContent;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.orderer.OrdererManagerContent;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.modulehost.ItemTarget;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.IHavePriority;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Offers the items in the inventories on every side to requests from the network. Its built-in provider module, the
 * same one that goes into a chassis, makes the promises, sends the items and keeps the settings (filter, extraction
 * mode); the pipe keeps its HUD and its order manager, which the module uses.
 */
public class PipeItemsProviderLogistics extends CoreRoutedPipe
        implements IProvideItems, IHeadUpDisplayRendererProvider, IChestContentReceiver, IChangeListener,
        IOrderManagerContentReceiver, IMUICompatiblePipeV2, IStagedProviderReservation, IHavePriority {

    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    private final Map<ItemIdentifier, Integer> displayMap = new TreeMap<>();
    public final ArrayList<ItemIdentifierStack> displayList = new ArrayList<>();
    private final ArrayList<ItemIdentifierStack> oldList = new ArrayList<>();

    public final LinkedList<ItemIdentifierStack> oldManagerList = new LinkedList<>();
    public final LinkedList<ItemIdentifierStack> itemListOrderer = new LinkedList<>();
    private final HUDProvider HUD = new HUDProvider(this);

    protected LogisticsItemOrderManager _orderManager = new LogisticsItemOrderManager(this, this);
    private boolean doContentUpdate = true;

    public PipeItemsProviderLogistics(Item item) {
        super(item);
        hostModule(providerModuleId());
    }

    /**
     * @return the module the pipe is; the Mk2 has the faster one
     */
    protected int providerModuleId() {
        return ItemModule.PROVIDER;
    }

    private ModuleProvider module() {
        return (ModuleProvider) hostedModule();
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return hostedModule();
    }

    /**
     * The module's orders are the pipe's, so the HUD shows them.
     */
    @Override
    public LogisticsItemOrderManager getItemOrderManager() {
        return _orderManager;
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_PROVIDER_TEXTURE;
    }

    /**
     * The HUD; the module sends the items (every 6 ticks, as the pipe always did).
     */
    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (isNthTick(6)) {
            updateInv(null);
        }
        if (doContentUpdate) {
            checkContentUpdate(null);
        }
    }

    /*** IProvideItems, answered by the module ***/

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (isEnabled()) {
            module().canProvide(tree, root, filters);
        }
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        spawnParticle(Particles.WhiteParticle, 2);
        return module().fullFill(promise, destination, info);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> items, List<IFilter> filters) {
        if (isEnabled()) {
            module().getAllItems(items, filters);
        }
    }

    @Override
    public void reserveStagedCrafting(ItemIdentifier item, int amount) {
        module().reserveStagedCrafting(item, amount);
    }

    @Override
    public void releaseStagedCrafting(ItemIdentifier item, int amount) {
        module().releaseStagedCrafting(item, amount);
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    /*** HUD ***/

    @Override
    public void startWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStartWatchingPacket.class).setInteger(1 /* TODO */).setPosX(getX())
                        .setPosY(getY()).setPosZ(getZ()));
    }

    @Override
    public void stopWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStopWatchingPacket.class).setInteger(1 /* TODO */).setPosX(getX())
                        .setPosY(getY()).setPosZ(getZ()));
    }

    private void updateInv(EntityPlayer player) {
        if (localModeWatchers.size() == 0 && player == null) {
            return;
        }
        displayList.clear();
        displayMap.clear();
        getAllItems(displayMap, new ArrayList<>(0));
        displayList.ensureCapacity(displayMap.size());
        for (Entry<ItemIdentifier, Integer> item : displayMap.entrySet()) {
            displayList.add(new ItemIdentifierStack(item.getKey(), item.getValue()));
        }
        if (!oldList.equals(displayList)) {
            oldList.clear();
            oldList.ensureCapacity(displayList.size());
            oldList.addAll(displayList);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ChestContent.class).setIdentList(displayList).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        } else if (player != null) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(ChestContent.class).setIdentList(displayList).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
        }
    }

    @Override
    public void listenedChanged() {
        doContentUpdate = true;
    }

    private void checkContentUpdate(EntityPlayer player) {
        doContentUpdate = false;
        LinkedList<ItemIdentifierStack> all = _orderManager.getContentList(getWorld());
        if (!oldManagerList.equals(all)) {
            oldManagerList.clear();
            oldManagerList.addAll(all);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(all).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        } else if (player != null) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(all).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
        }
    }

    @Override
    public void playerStartWatching(EntityPlayer player, int mode) {
        if (mode == 1) {
            localModeWatchers.add(player);
            updateInv(player);
            checkContentUpdate(player);
        } else {
            super.playerStartWatching(player, mode);
        }
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
        super.playerStopWatching(player, mode);
        localModeWatchers.remove(player);
    }

    @Override
    public void setReceivedChestContent(Collection<ItemIdentifierStack> list) {
        displayList.clear();
        displayList.ensureCapacity(list.size());
        displayList.addAll(list);
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return HUD;
    }

    @Override
    public void setOrderManagerContent(Collection<ItemIdentifierStack> list) {
        itemListOrderer.clear();
        itemListOrderer.addAll(list);
    }

    /**
     * Everything in the inventories next to it, as the pipe always announced (the module in a chassis announces by its
     * filter instead).
     */
    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> interests = null;
        for (ItemTarget target : getItemTargets(getExtractionMode(), true)) {
            if (interests == null) {
                interests = new TreeSet<>();
            }
            interests.addAll(target.inventory.getItems());
        }
        return interests;
    }

    @Override
    public double getLoadFactor() {
        return (_orderManager.totalAmountCountInAllOrders() + 63) / 64.0;
    }

    /*** settings, kept by the module ***/

    public ItemIdentifierInventory getprovidingInventory() {
        return (ItemIdentifierInventory) module().getFilterInventory();
    }

    public boolean hasFilter() {
        return !getprovidingInventory().isEmpty();
    }

    public boolean itemIsFiltered(ItemIdentifier item) {
        return getprovidingInventory().containsItem(item);
    }

    public boolean isExcludeFilter() {
        return module().isExcludeFilter();
    }

    public ExtractionMode getExtractionMode() {
        return module().getExtractionMode();
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromModule(this, module());
    }

    /**
     * The priority of the built-in provider: which provider a request asks first.
     */
    @Override
    public int getPriority() {
        return module() == null ? 0 : module().getPriority();
    }
}
