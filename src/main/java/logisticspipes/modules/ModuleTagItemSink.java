package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.LogisticsPipes;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.hud.modules.HUDStringBasedItemSink;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleTagItemSinkMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.interfaces.IStringBasedModule;
import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.guis.module.inhand.StringBasedItemSinkModuleGuiInHand;
import logisticspipes.network.guis.module.inpipe.StringBasedItemSinkModuleGuiSlot;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.module.ItemSinkListPacket;
import logisticspipes.pipes.modulehost.ModuleSlotTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.astar.CategoryInterestsCache;
import logisticspipes.routing.astar.ItemCategory;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;

/**
 * Takes items by a tag: their mod, their creative tab, their ore dictionary names or their GregTech prefix (one mode
 * per module), up to {@link #MAX_ENTRIES} values. It replaces the mod based, creative tab, ore dictionary and GT type
 * filter sinks (docs/module-rework.md); each mode keeps the priority its old module had.
 */
public class ModuleTagItemSink extends LogisticsGuiModule implements IClientInformationProvider, IHUDModuleHandler,
        IModuleWatchReciver, IStringBasedModule, IMUICompatibleModule, IPrioritizedModule {

    public static final int MAX_ENTRIES = 9;

    public static final String MODE_KEY = "tagMode";

    public enum TagMode {

        MOD("Mod", FixedPriority.ModBasedItemSink, ItemCategory.MOD),
        CREATIVE_TAB("Creative tab", FixedPriority.ModBasedItemSink, ItemCategory.CREATIVE_TAB),
        ORE_DICT("Ore dictionary", FixedPriority.OreDictItemSink, ItemCategory.ITEM),
        // every item is a candidate: the prefix of an item can't be looked up without GregTech
        GT_PREFIX("GT prefix", FixedPriority.GT_TypeFilterSink, null);

        public final String label;
        final FixedPriority priority;
        final ItemCategory category;

        TagMode(String label, FixedPriority priority, ItemCategory category) {
            this.label = label;
            this.priority = priority;
            this.category = category;
        }

        /**
         * @return false for the GT prefix mode without GregTech
         */
        public boolean isAvailable() {
            return this != GT_PREFIX || LogisticsPipes.isGTNH;
        }
    }

    public final List<String> entries = new LinkedList<>();

    private TagMode mode;

    // built from the entries when needed, dropped when they or the mode change
    private Set<String> entrySet;
    private Map<Item, Set<Integer>> oreItems;
    private Set<Object> gtPrefixes;
    private final Map<TagMode, CategoryInterestsCache> categoryInterests = new HashMap<>();

    private SinkReply sinkReply;

    // scratch, single-slot inventory the MUI uses to look up the tags of an item; never saved
    private final ItemIdentifierInventory analyseInventory = new ItemIdentifierInventory(1, "Analyse Slot", 1);

    private final IHUDModuleRenderer HUD = new HUDStringBasedItemSink(this);
    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    public ModuleTagItemSink() {
        this(TagMode.MOD);
    }

    /**
     * @param defaultMode the mode of settings saved without one: the old sinks saved only their list
     */
    protected ModuleTagItemSink(TagMode defaultMode) {
        mode = defaultMode;
    }

    public TagMode getMode() {
        return mode;
    }

    /**
     * Switches the mode; a mode that isn't available (GT prefix without GregTech) becomes the mod mode. The entries
     * stay.
     */
    public void setMode(TagMode mode) {
        this.mode = mode.isAvailable() ? mode : TagMode.MOD;
        sinkReply = null;
        entriesChanged();
    }

    public ItemIdentifierInventory getAnalyseInventory() {
        return analyseInventory;
    }

    /**
     * Adds the values that aren't in the list yet, as far as there is room.
     */
    public void addEntries(List<String> values) {
        boolean changed = false;
        for (String value : values) {
            if (!value.isEmpty() && !entries.contains(value) && entries.size() < MAX_ENTRIES) {
                entries.add(value);
                changed = true;
            }
        }
        if (changed) {
            entriesChanged();
        }
    }

    public void removeEntry(String value) {
        if (entries.remove(value)) {
            entriesChanged();
        }
    }

    public void setEntries(List<String> values) {
        entries.clear();
        for (String value : values) {
            if (entries.size() < MAX_ENTRIES) {
                entries.add(value);
            }
        }
        entriesChanged();
    }

    private void entriesChanged() {
        entrySet = null;
        oreItems = null;
        gtPrefixes = null;
    }

    /**
     * @return the values of the item for the current mode: its mod, its tab, its ore names or its GT prefix
     */
    public List<String> getTagsOf(ItemIdentifier item) {
        if (item == null) {
            return Collections.emptyList();
        }
        switch (mode) {
            case MOD:
                return single(item.getModName());
            case CREATIVE_TAB:
                return single(item.getCreativeTabName());
            case ORE_DICT: {
                List<String> names = new ArrayList<>();
                ItemStack stack = item.makeNormalStack(1);
                for (int id : OreDictionary.getOreIDs(stack)) {
                    names.add(OreDictionary.getOreName(id));
                }
                return names;
            }
            case GT_PREFIX:
                return mode.isAvailable() ? single(TagSinkGregTech.prefixOf(item)) : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

    private static List<String> single(String value) {
        return value == null || value.isEmpty() ? Collections.emptyList() : Collections.singletonList(value);
    }

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        sinkReply = null;
    }

    private SinkReply reply() {
        if (sinkReply == null) {
            sinkReply = new SinkReply(
                    mode.priority,
                    0,
                    true,
                    false,
                    5,
                    0,
                    new ModuleSlotTargetInformation(getPositionInt()));
        }
        return prioritized(sinkReply);
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        SinkReply reply = reply();
        if (bestPriority > reply.fixedPriority.ordinal()
                || (bestPriority == reply.fixedPriority.ordinal() && bestCustomPriority >= reply.customPriority)) {
            return null;
        }
        if (!matches(item)) {
            return null;
        }
        // the mod and tab sinks checked for power, the ore dictionary and GT ones didn't
        if ((mode == TagMode.MOD || mode == TagMode.CREATIVE_TAB) && !_service.canUseEnergy(5)) {
            return null;
        }
        return reply;
    }

    private boolean matches(ItemIdentifier item) {
        switch (mode) {
            case MOD:
                return entrySet().contains(item.getModName());
            case CREATIVE_TAB:
                return entrySet().contains(item.getCreativeTabName());
            case ORE_DICT: {
                Set<Integer> damages = oreItems().get(item.item);
                return damages != null && (damages.isEmpty() || damages.contains(item.itemDamage));
            }
            case GT_PREFIX:
                return mode.isAvailable() && TagSinkGregTech.matches(gtPrefixes(), item);
            default:
                return false;
        }
    }

    private Set<String> entrySet() {
        if (entrySet == null) {
            entrySet = new HashSet<>(entries);
        }
        return entrySet;
    }

    // item -> the damage values the ore names cover, an empty set for every damage
    private Map<Item, Set<Integer>> oreItems() {
        if (oreItems == null) {
            Map<Item, Set<Integer>> map = new HashMap<>();
            for (String name : entries) {
                for (ItemStack stack : OreDictionary.getOres(name)) {
                    if (stack == null) {
                        continue;
                    }
                    if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                        map.put(stack.getItem(), new TreeSet<>());
                    } else {
                        Set<Integer> damages = map.get(stack.getItem());
                        if (damages == null) {
                            damages = new TreeSet<>();
                            damages.add(stack.getItemDamage());
                            map.put(stack.getItem(), damages);
                        } else if (!damages.isEmpty()) {
                            damages.add(stack.getItemDamage());
                        }
                    }
                }
            }
            oreItems = map;
        }
        return oreItems;
    }

    private Set<Object> gtPrefixes() {
        if (gtPrefixes == null) {
            gtPrefixes = TagSinkGregTech.prefixes(entries);
        }
        return gtPrefixes;
    }

    /**
     * Without a category (GT prefix) every item is a candidate.
     */
    @Override
    public boolean hasGenericInterests() {
        return mode.category == null;
    }

    /**
     * Only the items with the values of the list are offered: by mod, by tab, or by the items the ore names stand for
     * (by the item alone, the damage values are checked in sinksItem).
     */
    @Override
    public CategoryInterests getCategoryInterests() {
        if (mode.category == null) {
            return null;
        }
        CategoryInterestsCache cache = categoryInterests
                .computeIfAbsent(mode, m -> new CategoryInterestsCache(m.category));
        return mode == TagMode.ORE_DICT ? cache.get(oreItems().keySet()) : cache.get(entrySet());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        readPriority(nbt);
        if (nbt.hasKey(MODE_KEY)) {
            try {
                mode = TagMode.valueOf(nbt.getString(MODE_KEY));
            } catch (IllegalArgumentException e) {
                mode = TagMode.MOD;
            }
            if (!mode.isAvailable()) {
                mode = TagMode.MOD;
            }
        }
        entries.clear();
        if (nbt.hasKey("size")) {
            // the GT type filter sink
            int size = nbt.getInteger("size");
            for (int i = 0; i < size; i++) {
                addRead(nbt.getString("prefix_" + i));
            }
        } else {
            // this module, the mod / tab sinks ("Mod") and the ore dictionary sink ("Ore")
            int size = nbt.getInteger("listSize");
            for (int i = 0; i < size; i++) {
                String value = nbt.getString("Entry" + i);
                if (value.isEmpty()) {
                    value = nbt.getString("Mod" + i);
                }
                if (value.isEmpty()) {
                    value = nbt.getString("Ore" + i);
                }
                addRead(value);
            }
        }
        sinkReply = null;
        entriesChanged();
    }

    private void addRead(String value) {
        if (!value.isEmpty() && entries.size() < MAX_ENTRIES) {
            entries.add(value);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        writePriority(nbt);
        nbt.setString(MODE_KEY, mode.name());
        nbt.setInteger("listSize", entries.size());
        for (int i = 0; i < entries.size(); i++) {
            nbt.setString("Entry" + i, entries.get(i));
        }
    }

    @Override
    public void tick() {}

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public List<String> getClientInformation() {
        List<String> list = new ArrayList<>();
        list.add(mode.label + ":");
        list.addAll(entries);
        return list;
    }

    // the legacy string list GUI and HUD

    @Override
    public List<String> getStringList() {
        return entries;
    }

    @Override
    public String getStringForItem(ItemIdentifier ident) {
        List<String> tags = getTagsOf(ident);
        return tags.isEmpty() ? "" : tags.get(0);
    }

    @Override
    public void listChanged() {
        entriesChanged();
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        if (MainProxy.isServer(_world.getWorld())) {
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ItemSinkListPacket.class).setNbt(nbt).setModulePos(this),
                    localModeWatchers);
        } else {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(ItemSinkListPacket.class).setNbt(nbt).setModulePos(this));
        }
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return NewGuiHandler.getGui(StringBasedItemSinkModuleGuiSlot.class).setNbt(nbt);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(StringBasedItemSinkModuleGuiInHand.class);
    }

    @Override
    public void startHUDWatching() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStartModuleWatchingPacket.class).setModulePos(this));
    }

    @Override
    public void stopHUDWatching() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStopModuleWatchingPacket.class).setModulePos(this));
    }

    @Override
    public IHUDModuleRenderer getHUDRenderer() {
        return HUD;
    }

    @Override
    public void startWatching(EntityPlayer player) {
        localModeWatchers.add(player);
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ItemSinkListPacket.class).setNbt(nbt).setModulePos(this),
                player);
    }

    @Override
    public void stopWatching(EntityPlayer player) {
        localModeWatchers.remove(player);
    }

    @Override
    public List<ItemIdentifier> getSpecificInterests() {
        return null;
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
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleModBasedItemSink");
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleTagItemSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleTagItemSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleTagItemSinkMuiDynamic(this, prefix);
    }
}
