package logisticspipes.pipes.modulehost;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.IBufferItems;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ModuleMigration;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Holds the modules of a pipe: the module items, the modules made from them, the upgrades of each module and the
 * managers for what the modules work on (the inventory, the tank). Arrivals are handed to the module they are for.
 * <p>
 * The pipe keeps what is its own: which way it points, its GUI, HUD and textures. It listens to
 * {@link #getModuleInventory()} and calls {@link #onModuleInventoryChanged()} when the module items change; the host
 * answers with {@link IModuleHostPipe#onHostModulesChanged(boolean)}.
 * <p>
 * Two kinds, see the factories: the chassis host has module slots the player fills, its NBT keys are the ones the
 * chassis used before the host existed, so old chassis load unchanged. A single-module pipe has one fixed module that
 * never leaves the pipe; the pipe saves the module's settings itself (as before), the host keeps the rest under its own
 * key so it can't collide with the pipe's keys.
 */
public class ModuleHost {

    private static final String MODULE_INVENTORY_KEY = "chassi";
    private static final String MODULE_DATA_KEY = "slot";
    private static final String SINGLE_MODULE_KEY = "moduleHost";

    private final CoreRoutedPipe pipe;
    private final IModuleHostPipe hostPipe;
    private final LogisticsModule[] modules;
    private final ItemIdentifierInventory moduleInventory;
    // upgrades left in a slot without a module (old chassis kept upgrades per slot); the next module takes them
    private final ModuleUpgradeManager[] upgradeManagers;
    private final ItemHostResourceManager itemManager;
    private final FluidHostResourceManager fluidManager;
    private final List<IHostResourceManager> managers;
    // the module item of a single-module pipe, null for a chassis
    private final ItemStack fixedModule;

    /**
     * A host whose module slots the player fills, for a chassis.
     */
    public static <P extends CoreRoutedPipe & IModuleHostPipe> ModuleHost forChassis(P pipe, int slotCount) {
        return new ModuleHost(pipe, slotCount, null);
    }

    /**
     * A host with one fixed module, for a pipe that is that module: the module is made now and never leaves.
     */
    public static <P extends CoreRoutedPipe & IModuleHostPipe> ModuleHost forSingleModule(P pipe,
            ItemStack moduleItem) {
        ModuleHost host = new ModuleHost(pipe, 1, moduleItem.copy());
        host.installFixedModule();
        return host;
    }

    private <P extends CoreRoutedPipe & IModuleHostPipe> ModuleHost(P pipe, int slotCount, ItemStack fixedModule) {
        this.pipe = pipe;
        this.fixedModule = fixedModule;
        hostPipe = pipe;
        modules = new LogisticsModule[slotCount];
        moduleInventory = new ItemIdentifierInventory(slotCount, "Module host", 1);
        upgradeManagers = new ModuleUpgradeManager[slotCount];
        for (int i = 0; i < slotCount; i++) {
            upgradeManagers[i] = new ModuleUpgradeManager(pipe, pipe.getOriginalUpgradeManager());
        }
        itemManager = new ItemHostResourceManager(pipe, this);
        fluidManager = new FluidHostResourceManager(pipe, this);
        managers = Arrays.asList(itemManager, fluidManager);
    }

    /*** modules ***/

    public int getSlotCount() {
        return modules.length;
    }

    /**
     * @return the module in that slot, or null if the slot is empty or doesn't exist
     */
    public LogisticsModule getModule(int slot) {
        if (slot < 0 || slot >= modules.length) {
            return null;
        }
        return modules[slot];
    }

    public boolean hasModule(int slot) {
        return getModule(slot) != null;
    }

    private void installModule(int slot, LogisticsModule module) {
        if (modules[slot] != module) {
            notifyRemoved(modules[slot]);
        }
        modules[slot] = module;
    }

    public void removeModule(int slot) {
        notifyRemoved(modules[slot]);
        modules[slot] = null;
    }

    private void notifyRemoved(LogisticsModule module) {
        if (module instanceof LogisticsFluidModule fluidModule && MainProxy.isServer(pipe.getWorld())) {
            fluidModule.onRemoved();
        }
    }

    public ItemIdentifierInventory getModuleInventory() {
        return moduleInventory;
    }

    /**
     * @return the upgrades of the module in that slot, or for an empty slot the upgrades left in it
     */
    public ModuleUpgradeManager getUpgradeManager(int slot) {
        LogisticsModule module = getModule(slot);
        return module != null ? module.getModuleUpgrades() : upgradeManagers[slot];
    }

    /**
     * Binds the module's upgrades to this pipe and hands it the upgrades left in its slot, as far as they fit.
     */
    private void adoptUpgrades(int slot, LogisticsModule module) {
        module.getModuleUpgrades().bind(pipe, pipe.getOriginalUpgradeManager());
        if (!upgradeManagers[slot].isEmpty()) {
            upgradeManagers[slot].moveInto(module.getModuleUpgrades());
        }
    }

    /**
     * Makes the modules match the module items: installs the module of a new item, removes the module of an emptied
     * slot, and tells the managers and the pipe.
     *
     * @return true if a module was removed, so an open GUI has to be rebuilt
     */
    public boolean onModuleInventoryChanged() {
        boolean removed = false;
        for (int i = 0; i < moduleInventory.getSizeInventory(); i++) {
            ItemStack stack = moduleInventory.getStackInSlot(i);
            if (stack == null) {
                if (hasModule(i)) {
                    removeModule(i);
                    removed = true;
                }
                continue;
            }

            if (stack.getItem() instanceof ItemModule) {
                // a module the rework merged into another: the replacing module with the speed it needs. The server
                // does it, the client gets the new item with the next update. A single-module pipe keeps its own
                if (ModuleMigration.isLegacy(stack) && !isSingleModule() && !MainProxy.isClient()) {
                    stack = ModuleMigration.migrate(stack);
                }
                LogisticsModule current = modules[i];
                LogisticsModule next = ((ItemModule) stack.getItem()).getModuleForItem(stack, current, pipe, pipe);
                if (next == null) {
                    // a module that no longer exists, the item of it stays in the slot and does nothing
                    if (hasModule(i)) {
                        removeModule(i);
                        removed = true;
                    }
                    continue;
                }
                next.registerPosition(positionType(), i);
                next.registerCCEventQueuer(pipe);
                if (current != next) {
                    installModule(i, next);
                    if (!MainProxy.isClient()) {
                        ItemModuleInformationManager.readInformation(stack, next);
                    }
                    ItemModuleInformationManager.removeInformation(stack);
                    adoptUpgrades(i, next);
                }
                moduleInventory.setInventorySlotContents(i, stack);
            }
        }
        for (IHostResourceManager manager : managers) {
            manager.onModulesChanged();
        }
        hostPipe.onHostModulesChanged(removed);
        return removed;
    }

    /**
     * Registers the modules at their slots again, after the pipe got its tile.
     */
    public void registerPositions() {
        for (int i = 0; i < modules.length; i++) {
            if (modules[i] != null) {
                modules[i].registerPosition(positionType(), i);
                modules[i].getModuleUpgrades().bind(pipe, pipe.getOriginalUpgradeManager());
            }
        }
    }

    /**
     * @return true if the pipe is a single fixed module
     */
    public boolean isSingleModule() {
        return fixedModule != null;
    }

    /**
     * @return where the modules sit: in the slots of a chassis, or in the pipe for a single-module pipe (modules like
     *         the item sink check room themselves only there; in a chassis the chassis module does it)
     */
    private ModulePositionType positionType() {
        return isSingleModule() ? ModulePositionType.IN_PIPE : ModulePositionType.SLOT;
    }

    private void installFixedModule() {
        moduleInventory.setInventorySlotContents(0, fixedModule.copy());
        onModuleInventoryChanged();
    }

    /**
     * Drops the module items, each with the settings of its module, when the pipe is removed. Server side only. A
     * single-module pipe drops nothing: its module is part of the pipe item.
     */
    public void dropModules() {
        for (IHostResourceManager manager : managers) {
            manager.onRemoved();
        }
        // upgrades left in slots without a module go with no item, they drop on their own
        for (ModuleUpgradeManager leftovers : upgradeManagers) {
            leftovers.getInv().dropContents(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
        }
        if (isSingleModule()) {
            // the module is part of the pipe item, its upgrades are not
            if (modules[0] != null && modules[0].hasModuleUpgrades()) {
                modules[0].getModuleUpgrades().getInv()
                        .dropContents(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
            }
            return;
        }
        for (int i = 0; i < moduleInventory.getSizeInventory(); i++) {
            ItemIdentifierStack ms = moduleInventory.getIDStackInSlot(i);
            if (ms != null) {
                ItemStack s = ms.makeNormalStack();
                ItemModuleInformationManager.saveInfotmation(s, modules[i]);
                moduleInventory.setInventorySlotContents(i, s);
            }
        }
        moduleInventory.dropContents(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
    }

    public void tickModules() {
        for (LogisticsModule module : modules) {
            if (module != null) {
                module.tick();
            }
        }
    }

    public void tickManagers() {
        for (IHostResourceManager manager : managers) {
            manager.tick();
        }
    }

    /*** resource managers ***/

    public ItemHostResourceManager getItemManager() {
        return itemManager;
    }

    public FluidHostResourceManager getFluidManager() {
        return fluidManager;
    }

    public List<IHostResourceManager> getManagers() {
        return managers;
    }

    /**
     * @return the manager an item sent to this host is for
     */
    public IHostResourceManager getManagerFor(ItemIdentifierStack stack, IAdditionalTargetInformation info) {
        if (info instanceof ModuleSlotTargetInformation target) {
            LogisticsModule module = getModule(target.getModuleSlot());
            if (module != null) {
                return fluidManager.ownsModule(module) ? fluidManager : itemManager;
            }
        }
        if (FluidHostResourceManager.isFluidInTransit(stack)) {
            return fluidManager;
        }
        if (itemManager.isActive() && itemManager.wantsArrival(stack)) {
            return itemManager;
        }
        if (fluidManager.isActive() && fluidManager.wantsArrival(stack)) {
            return fluidManager;
        }
        return itemManager;
    }

    /**
     * Takes an item that reached the end of the pipe.
     *
     * @return true if the item is handled, false lets it go into the inventory the pipe points at
     */
    public boolean takeArrival(LPTravelingItemServer arrivingItem) {
        ItemIdentifierStack stack = arrivingItem.getItemIdentifierStack();
        if (stack == null) {
            return false;
        }
        IAdditionalTargetInformation info = arrivingItem.getInfo() == null ? null : arrivingItem.getInfo().targetInfo;
        return getManagerFor(stack, info).takeArrival(arrivingItem);
    }

    /*** arrivals for a module ***/

    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (!MainProxy.isServer(pipe.getWorld())) {
            return;
        }
        if (info instanceof ModuleSlotTargetInformation target) {
            if (getModule(target.getModuleSlot()) instanceof IRequireReliableTransport module) {
                module.itemArrived(item, info);
            }
        } else if (info != null) {
            logForeignInfo("itemArrived", item, info);
        }
    }

    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (!MainProxy.isServer(pipe.getWorld())) {
            return;
        }
        if (info instanceof ModuleSlotTargetInformation target) {
            if (getModule(target.getModuleSlot()) instanceof IRequireReliableTransport module) {
                module.itemLost(item, info);
            }
        } else if (info != null) {
            logForeignInfo("itemLost", item, info);
        }
    }

    /**
     * @return how many of the items were not taken
     */
    public int addToBuffer(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (MainProxy.isServer(pipe.getWorld())) {
            if (info instanceof ModuleSlotTargetInformation target) {
                if (getModule(target.getModuleSlot()) instanceof IBufferItems module) {
                    return module.addToBuffer(item, info);
                }
            } else if (info != null) {
                logForeignInfo("addToBuffer", item, info);
            }
        }
        return item.getStackSize();
    }

    // target information that doesn't name a module slot of this host
    private void logForeignInfo(String what, ItemIdentifierStack item, IAdditionalTargetInformation info) {
        LogisticsPipes.log.debug("[{}] {}: target information {} isn't for a module host", what, item, info);
    }

    /*** interests: a module only counts while the pipe points at something its manager works on ***/

    public void addSpecificInterests(Set<ItemIdentifier> interests) {
        for (IHostResourceManager manager : managers) {
            if (manager.isActive()) {
                manager.addSpecificInterests(interests);
            }
        }
    }

    public boolean hasGenericInterests() {
        for (IHostResourceManager manager : managers) {
            if (manager.isActive() && manager.hasGenericInterests()) {
                return true;
            }
        }
        return false;
    }

    public CategoryInterests getCategoryInterests() {
        CategoryInterests interests = CategoryInterests.EMPTY;
        for (IHostResourceManager manager : managers) {
            if (manager.isActive()) {
                interests = interests.merge(manager.getCategoryInterests());
            }
        }
        return interests;
    }

    /*** NBT ***/

    /**
     * Reads the module items, installs their modules, then reads the module data with the modules' upgrades, the
     * upgrades left in slots and the managers.
     * <p>
     * Chassis saved before the modules owned their upgrades have them under the slot keys only: they are read as
     * upgrades left in the slot and handed to the module of the slot.
     */
    public void readFromNBT(NBTTagCompound pipeTag) {
        NBTTagCompound nbttagcompound;
        int[] migratedSpeed = new int[modules.length];
        if (isSingleModule()) {
            // the pipe read the module's settings already; pipes saved before the host have no host key, that's fine
            nbttagcompound = pipeTag.getCompoundTag(SINGLE_MODULE_KEY);
        } else {
            nbttagcompound = pipeTag;
            moduleInventory.readFromNBT(nbttagcompound, MODULE_INVENTORY_KEY);
            migratedSpeed = replaceLegacyModules(nbttagcompound);
            // reading the inventory doesn't fire its listeners
            onModuleInventoryChanged();
            readModulesFromNBT(nbttagcompound);
        }
        if (isSingleModule() && modules[0] != null
                && ModuleUpgradeManager
                        .hasUpgradesInNBT(nbttagcompound, ItemModuleInformationManager.MODULE_UPGRADES_PREFIX)) {
            modules[0].getModuleUpgrades()
                    .readFromNBT(nbttagcompound, ItemModuleInformationManager.MODULE_UPGRADES_PREFIX);
        }
        for (int i = 0; i < upgradeManagers.length; i++) {
            upgradeManagers[i].readFromNBT(nbttagcompound, Integer.toString(i));
            if (modules[i] != null) {
                adoptUpgrades(i, modules[i]);
            }
        }
        // last: the module's upgrades were read from the slot data above
        for (int i = 0; i < modules.length; i++) {
            if (migratedSpeed[i] > 0 && modules[i] != null) {
                ModuleMigration.addSpeedUpgrades(modules[i].getModuleUpgrades(), migratedSpeed[i]);
            }
        }
        for (IHostResourceManager manager : managers) {
            manager.readFromNBT(nbttagcompound);
        }
    }

    /**
     * Replaces the loaded items of modules the rework merged into another with the replacing module. Its settings are
     * in the slot data and still fit; the speed upgrades it gets are added once its upgrades are read.
     *
     * @return the speed upgrades each slot's module gets
     */
    private int[] replaceLegacyModules(NBTTagCompound nbttagcompound) {
        int[] speed = new int[modules.length];
        if (MainProxy.isClient()) {
            return speed;
        }
        for (int i = 0; i < moduleInventory.getSizeInventory(); i++) {
            ItemStack stack = moduleInventory.getStackInSlot(i);
            if (ModuleMigration.isLegacy(stack)) {
                speed[i] = ModuleMigration.speedUpgradesFor(stack.getItemDamage());
                NBTTagCompound settings = nbttagcompound.getCompoundTag(MODULE_DATA_KEY + i);
                ModuleMigration.migrateSettings(stack.getItemDamage(), settings);
                nbttagcompound.setTag(MODULE_DATA_KEY + i, settings);
                ItemStack replaced = stack.copy();
                replaced.setItemDamage(ModuleMigration.replacementFor(stack.getItemDamage()));
                moduleInventory.setInventorySlotContents(i, replaced);
            }
        }
        return speed;
    }

    public void writeToNBT(NBTTagCompound pipeTag) {
        NBTTagCompound nbttagcompound;
        if (isSingleModule()) {
            nbttagcompound = new NBTTagCompound();
            pipeTag.setTag(SINGLE_MODULE_KEY, nbttagcompound);
            // the module's own upgrades; its settings are saved by the pipe
            LogisticsModule module = modules[0];
            if (module != null && module.hasModuleUpgradeData()) {
                module.getModuleUpgrades()
                        .writeToNBT(nbttagcompound, ItemModuleInformationManager.MODULE_UPGRADES_PREFIX);
            }
        } else {
            nbttagcompound = pipeTag;
            moduleInventory.writeToNBT(nbttagcompound, MODULE_INVENTORY_KEY);
            writeModulesToNBT(nbttagcompound);
        }
        for (int i = 0; i < upgradeManagers.length; i++) {
            upgradeManagers[i].writeToNBT(nbttagcompound, Integer.toString(i));
        }
        for (IHostResourceManager manager : managers) {
            manager.writeToNBT(nbttagcompound);
        }
    }

    /**
     * Reads the data of the installed modules only.
     */
    private void readModulesFromNBT(NBTTagCompound nbttagcompound) {
        for (int i = 0; i < modules.length; i++) {
            if (modules[i] != null) {
                NBTTagCompound slot = nbttagcompound.getCompoundTag(MODULE_DATA_KEY + i);
                if (slot != null) {
                    modules[i].readFromNBT(slot);
                    if (ModuleUpgradeManager
                            .hasUpgradesInNBT(slot, ItemModuleInformationManager.MODULE_UPGRADES_PREFIX)) {
                        modules[i].getModuleUpgrades()
                                .readFromNBT(slot, ItemModuleInformationManager.MODULE_UPGRADES_PREFIX);
                    }
                }
            }
        }
    }

    /**
     * Writes the data of the installed modules only.
     */
    private void writeModulesToNBT(NBTTagCompound nbttagcompound) {
        for (int i = 0; i < modules.length; i++) {
            if (modules[i] != null) {
                NBTTagCompound slot = new NBTTagCompound();
                modules[i].writeToNBT(slot);
                if (modules[i].hasModuleUpgradeData()) {
                    modules[i].getModuleUpgrades()
                            .writeToNBT(slot, ItemModuleInformationManager.MODULE_UPGRADES_PREFIX);
                }
                nbttagcompound.setTag(MODULE_DATA_KEY + i, slot);
            }
        }
    }
}
