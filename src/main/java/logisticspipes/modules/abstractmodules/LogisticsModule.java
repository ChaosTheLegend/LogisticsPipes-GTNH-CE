package logisticspipes.modules.abstractmodules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.interfaces.IPipeServiceProvider;
import logisticspipes.interfaces.IQueueCCEvent;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.proxy.computers.interfaces.ILPCCTypeHolder;
import logisticspipes.proxy.computers.objects.CCSinkResponder;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

@CCType(name = "LogisticsModule")
public abstract class LogisticsModule implements ISaveState, ILPCCTypeHolder {

    private Object ccType;

    protected IWorldProvider _world;
    protected IPipeServiceProvider _service;

    /**
     * Registers the Inventory and ItemSender to the module
     *
     * @param world   that the module is in.
     * @param service Inventory access, power and utility functions provided by the pipe
     */
    public void registerHandler(IWorldProvider world, IPipeServiceProvider service) {
        _world = world;
        _service = service;
    }

    public World getWorld() {
        return _world.getWorld();
    }

    public IPipeServiceProvider getService() {
        return _service;
    }

    // made on first use: most modules never get an upgrade
    private ModuleUpgradeManager moduleUpgrades;

    /**
     * Player-set priority of a module that takes or provides things ({@link IPrioritizedModule}): between modules of
     * the same kind the higher one wins, before distance.
     */
    public static final int MIN_PRIORITY = -10;
    public static final int MAX_PRIORITY = 10;
    // room between two priorities for a module's own custom priorities (the enchantment sink uses 0 and 1)
    private static final int PRIORITY_STEP = 4;
    private static final String PRIORITY_KEY = "priority";

    private int priority = 0;
    // reply -> the same reply with the priority added, built once per reply
    private final Map<SinkReply, SinkReply> prioritizedReplies = new ConcurrentHashMap<>();

    /**
     * @return the priority that works: the one set while a priority upgrade is installed, else 0
     */
    public int getPriority() {
        return hasPriorityUpgrade() ? priority : 0;
    }

    /**
     * @return the priority set in the settings window, also while it doesn't work (no priority upgrade)
     */
    public int getSetPriority() {
        return priority;
    }

    /**
     * @return true if the upgrades the module uses hold a priority upgrade
     */
    public boolean hasPriorityUpgrade() {
        if (_service != null && slot != null) {
            return _service.getUpgradeManager(slot, positionInt).hasPriorityUpgrade();
        }
        return moduleUpgrades != null && moduleUpgrades.hasPriorityUpgrade();
    }

    public void setPriority(int priority) {
        int clamped = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, priority));
        if (clamped != this.priority) {
            this.priority = clamped;
            prioritizedReplies.clear();
        }
    }

    /**
     * @return the reply with the module's priority added to its custom priority; the reply itself at priority 0
     */
    protected SinkReply prioritized(SinkReply reply) {
        // the cached replies carry the set priority; without the upgrade it is 0
        if (reply == null || getPriority() == 0) {
            return reply;
        }
        return prioritizedReplies
                .computeIfAbsent(reply, r -> r.withCustomPriority(r.customPriority + PRIORITY_STEP * priority));
    }

    protected void readPriority(NBTTagCompound nbt) {
        priority = 0;
        setPriority(nbt.getInteger(PRIORITY_KEY));
    }

    protected void writePriority(NBTTagCompound nbt) {
        if (priority != 0) {
            nbt.setInteger(PRIORITY_KEY, priority);
        }
    }

    /**
     * @return the upgrades of this module. They belong to the module: they stay with it when it is taken out of its
     *         pipe and are saved with its settings.
     */
    public ModuleUpgradeManager getModuleUpgrades() {
        if (moduleUpgrades == null) {
            moduleUpgrades = new ModuleUpgradeManager();
        }
        return moduleUpgrades;
    }

    /**
     * @return the speed upgrades that count for this module, up to {@link ModuleUpgradeManager#MAX_SPEED_UPGRADES}
     */
    public int getSpeedLevel() {
        return Math.min(ModuleUpgradeManager.MAX_SPEED_UPGRADES, getModuleUpgrades().getSpeedUpgradeCount());
    }

    /**
     * @return true if the module holds an upgrade
     */
    public boolean hasModuleUpgrades() {
        return moduleUpgrades != null && !moduleUpgrades.isEmpty();
    }

    /**
     * @return true if the module's upgrades have something to save: an upgrade or the sneaky faces set in the window
     */
    public boolean hasModuleUpgradeData() {
        return hasModuleUpgrades() || (moduleUpgrades != null && moduleUpgrades.getSneakyFaces().length > 0);
    }

    @Getter
    protected ModulePositionType slot;

    @Getter
    protected int positionInt;

    /**
     * Registers the slot type the module is in
     */
    public void registerPosition(ModulePositionType slot, int positionInt) {
        this.slot = slot;
        this.positionInt = positionInt;
        // modules build new replies here, the old ones would stay cached forever
        prioritizedReplies.clear();
    }

    public enum ModulePositionType {

        SLOT(true),
        IN_HAND(false),
        IN_PIPE(true);

        @Getter
        private final boolean inWorld;

        ModulePositionType(boolean inWorld) {
            this.inWorld = inWorld;
        }
    }

    /**
     * typically returns the coord of the pipe that holds it.
     */
    public abstract int getX();

    /**
     * typically returns the coord of the pipe that holds it.
     */
    public abstract int getY();

    /**
     * typically returns the coord of the pipe that holds it.
     */
    public abstract int getZ();

    /**
     * Gives an sink answer on the given itemstack
     *
     * @param stack              to sink
     * @param bestPriority       best priority seen so far
     * @param bestCustomPriority best custom subpriority
     * @param allowDefault       is a default only sink allowed to sink this?
     * @param includeInTransit   inclide the "in transit" items? -- true for a destination search, false for a sink
     *                           check.
     * @return SinkReply whether the module sinks the item or not
     */
    public abstract SinkReply sinksItem(ItemIdentifier stack, int bestPriority, int bestCustomPriority,
            boolean allowDefault, boolean includeInTransit);

    /**
     * Returns submodules. Normal modules don't have submodules
     *
     * @param slot slotnumber of the requested module
     */
    public abstract LogisticsModule getSubModule(int slot);

    /**
     * A tick for the Module
     */
    public abstract void tick();

    /**
     * Is this module interested in all items, or just some specific ones?
     *
     * @return true: this module will be checked against every item request false: only requests involving items
     *         returned by getSpecificInterestes() will be checked
     */
    public abstract boolean hasGenericInterests();

    /**
     * the list of items which this module is capable of providing or supplying (or is otherwise interested in) the size
     * of the list here does not influence the ongoing computational cost.
     */
    public abstract Collection<ItemIdentifier> getSpecificInterests();

    /**
     * The values of item categories this module takes items by, for a module that would otherwise have to be interested
     * in all items. They must cover everything sinksItem can take.
     */
    public CategoryInterests getCategoryInterests() {
        return CategoryInterests.EMPTY;
    }

    public abstract boolean interestedInAttachedInventory();

    /**
     * is this module interested in receiving any damage variant of items in the attached inventory?
     */
    public abstract boolean interestedInUndamagedID();

    /**
     * is this module a valid destination for bounced items.
     */
    public abstract boolean recievePassive();

    /**
     * get The Icon for this Module Class
     */
    @SideOnly(Side.CLIENT)
    public abstract IIcon getIconTexture(IIconRegister register);

    /**
     * Returns whether the module should be displayed the effect when as an item.
     *
     * @return True to show effect False to no effect (default)
     */
    public boolean hasEffect() {
        return false;
    }

    /**
     * Called when an item routed to this module could not be (fully) inserted into the target inventory, so the module
     * can stop accepting it right away instead of attracting more items that will bounce.
     */
    public void insertionFailed(ItemIdentifier item) {}

    public List<CCSinkResponder> queueCCSinkEvent(ItemIdentifierStack item) {
        return new ArrayList<>(0);
    }

    public void registerCCEventQueuer(IQueueCCEvent eventQueuer) {}

    @CCCommand(description = "Returns if the Pipe has a gui")
    public boolean hasGui() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + "(" + getX() + ", " + getY() + ", " + getZ() + ")";
    }

    /**
     * typically used when the neighboring block changes
     */
    public void clearCache() {}

    @Override
    public void setCCType(Object type) {
        ccType = type;
    }

    @Override
    public Object getCCType() {
        return ccType;
    }
}
