package logisticspipes.gui.modularUI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.ISynced;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;

public class ChassisGui extends LogisticsModularUI {

    // the module panel under the tabs, and a tab
    private static final int PANEL_WIDTH = 224;
    private static final int TAB_SIZE = 28;

    private final PipeLogisticsChassi pipe;
    private final IItemHandlerModifiable moduleInventory;
    private final PagedWidget.Controller controller;

    private final List<DynamicSyncHandler> moduleSyncHandlers;
    // snapshot of each slot's live module, taken right before InventoryChanged tears it down, so the module
    // that was actually being edited can still be flushed onto the departing itemstack in onPickupFromSlot
    // (changeListener always fires before onPickupFromSlot, see ModularSlot.putStack -> onSlotChangedReal)
    private final LogisticsModule[] pendingFlushModule;
    private static final ResourceLocation ModuleSlotTexture1 = new ResourceLocation(
            "logisticspipes",
            "textures/gui/module_slot_1.png");

    public ChassisGui(PipeLogisticsChassi pipe) {
        this(pipe, "");
    }

    public ChassisGui(PipeLogisticsChassi pipe, String prefix) {
        super(prefix);
        this.pipe = pipe;
        moduleSyncHandlers = new ArrayList<>(pipe.getChassiSize());
        pendingFlushModule = new LogisticsModule[pipe.getChassiSize()];
        moduleInventory = new InvWrapper(pipe.getModuleInventory());
        controller = new PagedWidget.Controller();
    }

    @Override
    public String getId() {
        return "chassis_gui_module";
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        // ensure this side's live _modules[] matches the actual itemstacks before building the widget tree.
        // A module installed via any path OTHER than this GUI's own slot below (right-click-inserting an item
        // into the pipe while this GUI is closed, or a fresh world/chunk load - both go through
        // PipeLogisticsChassi#tryInsertingModule / #readFromNBT, which only ever run server-side) never
        // touches the CLIENT's copy of _modules[] - LogisticsPipes never syncs module state to the client
        // outside of this GUI's own slot changeListener below. Without this call, a client whose _modules[slot]
        // is still null would build the "No module in slot" branch of addModuleUI while the server builds the
        // real module UI - mismatched sync handler registration between the two sides means the server's
        // values are silently dropped by the client (PanelSyncManager.receiveWidgetUpdate: unknown key -> warn
        // + return), and the module UI never appears until the module is pulled out and reinserted through
        // this GUI's own slot (the one path that keeps both sides' _modules[] in lockstep).
        pipe.InventoryChanged(pipe.getModuleInventory());

        // self-heals a race confirmed via logging: right after a world/chunk reload (or right after a
        // module gets installed through a non-GUI path), this side's own module state can take a moment to
        // fully settle - not just "does a module exist in this slot" (PipeLogisticsChassi#readFromNBT installs
        // the module in one call to InventoryChanged() but only loads its real saved fields, e.g. extraction
        // mode/filter items, in the _module.readFromNBT(...) call right after) but the actual field values on
        // an already-installed module. addModuleUI's sync handlers snapshot whatever the module holds at the
        // moment the widget is built; nothing re-triggers that build once the module's fields settle a tick or
        // two later, since that settling never goes through markDirty()/this GUI's own slot changeListener.
        // Fingerprint each slot's module via its own NBT (cheap, and reflects real field values, not just
        // reference identity) and force a rebuild via notifyUpdate() whenever that fingerprint changes during
        // a brief settle window after open.
        String[] lastKnownModuleFingerprint = new String[pipe.getChassiSize()];
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            lastKnownModuleFingerprint[i] = moduleFingerprint(i);
        }
        int[] settleTicksRemaining = { 60 };
        if (!guiSyncManager.isClient()) {
            // the first sync of the columns can come before the client made its modules (right after the world
            // loads), it then lands nowhere: send them again once the client has them
            resendColumnsIn[0] = OPEN_RESEND_DELAY_TICKS;
        }
        guiSyncManager.onCommonTick(() -> {
            if (resendColumnsIn[0] > 0 && --resendColumnsIn[0] == 0 && !guiSyncManager.isClient()) {
                resendUpgradeColumns(guiSyncManager);
            }
            if (settleTicksRemaining[0] <= 0) return;
            settleTicksRemaining[0]--;
            pipe.InventoryChanged(pipe.getModuleInventory());
            for (int i = 0; i < pipe.getChassiSize(); i++) {
                String nowFingerprint = moduleFingerprint(i);
                if (!Objects.equals(nowFingerprint, lastKnownModuleFingerprint[i])) {
                    lastKnownModuleFingerprint[i] = nowFingerprint;
                    moduleSyncHandlers.get(i).notifyUpdate(packet -> {});
                }
            }
        });

        for (int i = 0; i < pipe.getChassiSize(); i++) {
            final int slot = i;
            // must go through the registrar (not just `new DynamicSyncHandler()`) so the handler actually gets
            // added to guiSyncManager's map - otherwise it never becomes isValid(), and notifyUpdate() below
            // silently no-ops forever (caches the call as lastRejectedPacket and returns), leaving the slot's
            // widget frozen at whatever it looked like when the GUI was first opened until the whole GUI is
            // closed and reopened
            DynamicSyncHandler handler = guiSyncManager.dynamicSyncHandler(
                    "chassis_module_dynamic_" + slot,
                    (innerSyncManager, packet) -> buildModuleWidget(innerSyncManager, slot)).allowC2S();
            moduleSyncHandlers.add(handler);
        }

        var panel = new ModularPanel(getId());

        panel.width(getWidth()).height(getHeight()).background(IDrawable.EMPTY);

        addWidgets(panel, guiSyncManager, true);

        var row = new Flow(GuiAxis.X).height(28).left(2).top(0);

        guiSyncManager.registerSlotGroup("module_inventory", pipe.getChassiSize());

        int lastSlot = pipe.getChassiSize() - 1;
        // the right edge tab only when the tabs reach the right side of the panel (8 slots), else it draws a corner
        // into the panel's top border
        boolean tabsFillRow = pipe.getChassiSize() * TAB_SIZE >= PANEL_WIDTH;
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            final int slot = i;
            var buttonContainer = new ParentWidget<>().size(28, 28);
            buttonContainer.child(
                    new PageButton(i, controller).size(28, 28)
                            .tab(GuiTextures.TAB_TOP, i == 0 ? -1 : i == lastSlot && tabsFillRow ? 1 : 0).pos(0, 0));
            buttonContainer.child(new ItemSlot().slot(new ModularSlot(moduleInventory, i) {

                @Override
                public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
                    // by now the changeListener below has already run InventoryChanged and torn
                    // down the live module for this slot; flush its last state (captured just
                    // before teardown) onto the itemstack that's actually leaving - it's a
                    // distinct object from whatever was in the slot (ItemStack#splitStack copies),
                    // so this must happen here rather than in the changeListener itself
                    LogisticsModule departingModule = pendingFlushModule[slot];
                    if (departingModule != null && stack != null) {
                        ItemModuleInformationManager.saveInfotmation(stack, departingModule);
                    }
                    pendingFlushModule[slot] = null;
                    super.onPickupFromSlot(player, stack);
                }
            }.slotGroup("module_inventory")
                    // only into an empty slot: swapping in place keeps the old module when the new one is of the same
                    // kind, and the item that leaves isn't saved first
                    .filter(stack -> isModuleItem(stack) && pipe.getModuleInventory().getStackInSlot(slot) == null)
                    .changeListener(((newItem, onlyAmountChanged, client, init) -> {
                        // "init" is replayed once by the framework every time the GUI is (re)opened, to hand the
                        // widget its current value - it does NOT mean the slot actually changed (see
                        // IOnSlotChanged#onChange javadoc). The normal top-level widget build (addWidgets ->
                        // addModuleUI) already builds this slot's module UI correctly on open, so reacting to
                        // this replay would just trigger a redundant rebuild via notifyUpdate() below.
                        if (init) {
                            return;
                        }
                        if (!onlyAmountChanged) {
                            pendingFlushModule[slot] = pipe.getModules().getSubModule(slot);
                        }
                        pipe.InventoryChanged(pipe.getModuleInventory());
                        if (client && !onlyAmountChanged) {
                            moduleSyncHandlers.get(slot).notifyUpdate(packet -> {});
                        }
                        if (!client && !onlyAmountChanged) {
                            // not now: the client learns about the new module with this tick's slot updates
                            resendColumnsIn[0] = RESEND_DELAY_TICKS;
                        }
                    }))).background(UITexture.fullImage(ModuleSlotTexture1)).pos(5, 5));
            row.child(buttonContainer);
        }
        panel.child(row);

        // the upgrades of the module on the open tab, at the right of the panel. One column per module slot, only
        // the open tab's one is shown: every column must exist on both sides (real container slots, the same on client
        // and server). Each column asks for the module of its slot every time, an empty slot takes nothing.
        // The client tells the server the open tab, so only its column takes a shift-clicked upgrade.
        int[] openTab = { 0 };
        IntSyncValue openTabSync = new IntSyncValue(() -> openTab[0], tab -> openTab[0] = tab).allowC2S();
        guiSyncManager.syncValue("open_tab", openTabSync);
        // the settings of the chassis: its sides (connect, the side it works on) and the priority of the module on the
        // open tab. Under the columns, placed with the first one
        var settings = PipeSettingsPanel.button(
                panel,
                guiSyncManager,
                "chassis_settings",
                pipe,
                () -> pipe.getModules().getSubModule(openTab[0]));
        int sideLeft = getWidth() - ModuleUpgradeSlots.FRAME_WIDTH - ModuleUpgradeSlots.GAP;
        // the server saves the pipe when an upgrade went in or out
        Runnable markDirty = () -> {
            if (pipe.container != null) {
                pipe.container.markDirty();
            }
        };
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            final int slot = i;
            ModuleUpgradeSlots.addSide(
                    panel,
                    sideLeft,
                    30,
                    ModuleUpgradeSlots.column(
                            guiSyncManager,
                            "module_upgrades_" + slot,
                            () -> pipe.getModules().getSubModule(slot),
                            markDirty,
                            () -> openTab[0] == slot),
                    slot == 0 ? settings : null).setEnabledIf(w -> {
                        if (!controller.isInitialised()) {
                            return false;
                        }
                        int active = controller.getActivePageIndex();
                        if (active != openTab[0]) {
                            openTabSync.setIntValue(active, true, true);
                        }
                        return active == slot;
                    });
        }

        guiSyncManager.addCloseListener(player -> {
            IInventory inv = pipe.getModuleInventory();
            for (int i = 0; i < pipe.getChassiSize(); i++) {
                LogisticsModule module = pipe.getModules().getSubModule(i);
                if (module == null) {
                    continue;
                }
                ItemStack stack = inv.getStackInSlot(i);
                if (stack == null) {
                    continue;
                }
                ItemModuleInformationManager.saveInfotmation(stack, module);
                inv.setInventorySlotContents(i, stack);
            }
        });

        return panel;
    }

    /**
     * Sends every upgrade column to the client again. A column shows the upgrades of whatever module is in its slot;
     * when the module changes, the server's column already holds the new module's upgrades but the client got no update
     * for them (its module there is new and empty), so a moved module showed no upgrades until the GUI was reopened.
     */
    private void resendUpgradeColumns(PanelSyncManager syncManager) {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            for (int index = 0; index < ModuleUpgradeManager.SLOTS; index++) {
                if (syncManager.findSyncHandlerNullable("module_upgrades_" + i, index) instanceof ItemSlotSH handler) {
                    handler.forceSyncItem();
                }
            }
        }
    }

    // ticks after a module change before the upgrade columns are sent again
    private static final int RESEND_DELAY_TICKS = 2;
    // ticks after the GUI opens before the upgrade columns are sent again
    private static final int OPEN_RESEND_DELAY_TICKS = 5;
    private final int[] resendColumnsIn = { 0 };

    private String moduleFingerprint(int slot) {
        LogisticsModule module = pipe.getModules().getSubModule(slot);
        if (module == null) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        module.writeToNBT(tag);
        return tag.toString();
    }

    private ParentWidget buildModuleWidget(PanelSyncManager innerSyncManager, int slot) {
        ParentWidget widget = addModuleUI(new Flow(GuiAxis.X).fullWidth().height(100), innerSyncManager, slot);

        // sync handlers created via this dynamic rebuild path never get PanelSyncManager's one-time "first
        // sync" push - that only covers handlers registered during the panel's original, synchronous build.
        // A freshly-created handler's cache starts out already matching its own local getter (trivially true
        // at construction), so the server never detects a "change" to push on its own, leaving the client
        // stuck on whatever its own local module held at construction (often blank/default) for the rest of
        // the session. Force every handler in the freshly-built tree to push its current value once, right
        // now - server side only, since doing this on the client would send its own (possibly stale) values
        // up and overwrite the server's authoritative state.
        if (!innerSyncManager.isClient()) {
            // explicit <IWidget> type witness is required: without it, javac infers T from `widget`'s
            // static type (ParentWidget), and the traversal's internal cast of every descendant to T then
            // throws ClassCastException on the first non-ParentWidget child (e.g. a TextWidget)
            WidgetTree.<IWidget>foreachChildBFS(widget, w -> {
                if (w instanceof ISynced<?>synced && synced.isSynced()) {
                    SyncHandler<?> handler = synced.getSyncHandler();
                    if (handler instanceof ValueSyncHandler<?, ?>valueHandler) {
                        valueHandler.notifyUpdate();
                    } else if (handler instanceof ItemSlotSH slotHandler) {
                        slotHandler.forceSyncItem();
                    }
                }
                return true;
            }, true);
        }

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return null;
    }

    private ParentWidget addModuleUI(ParentWidget widget, PanelSyncManager innerSyncManager, int slot) {

        ItemStack stack = pipe.getModuleInventory().getStackInSlot(slot);

        if (stack == null) {
            widget.child(centeredText("No module in slot"));
            return widget;
        }

        if (!(stack.getItem() instanceof ItemModule)) {
            widget.child(centeredText("Item is not a module"));
            return widget;
        }

        // the live, installed module for this slot - NOT a throwaway instance built from the itemstack,
        // so editing it here actually affects routing/pipe behavior
        LogisticsModule module = pipe.getModules().getSubModule(slot);

        if (module == null) {
            widget.child(centeredText("No module in slot"));
            return widget;
        }

        if (module instanceof ModuleCrafter) {
            widget.child(
                    centeredText("Crafting Modules are being replaced with Pattern Crafting Pipes, please use them"));
            return widget;
        }

        if (!(module instanceof IMUICompatibleModule compatibleModule)) {
            widget.child(centeredText("Module not compatible with MUI yet ＞﹏＜"));
            return widget;
        }

        // unique prefix per chassis slot, so two modules of the same type in different slots don't
        // collide on the same sync handler keys
        LogisticsModularUI gui = compatibleModule.getPipeGui("chassis_slot_" + slot);
        if (gui == null) {
            widget.child(centeredText("Module has no MUI"));
            return widget;
        }

        var moduleUI = new Flow(GuiAxis.Y).width(gui.getWidth()).fullHeight();
        gui.addWidgets(moduleUI, innerSyncManager, false);

        moduleUI.leftRel(Alignment.Center.x).anchorLeft(Alignment.Center.x);

        widget.fullWidth().fullHeight();
        widget.child(moduleUI);

        return widget;
    }

    private TextWidget<?> centeredText(String text) {
        return new TextWidget<>(text).leftRel(Alignment.Center.x).anchorLeft(Alignment.Center.x)
                .topRel(Alignment.Center.y).anchorTop(Alignment.Center.y);
    }

    private boolean isModuleItem(ItemStack itemStack) {
        return pipe.isModuleAllowed(itemStack);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        var mainPanel = new Flow(GuiAxis.Y);

        mainPanel.width(PANEL_WIDTH).height(180).left(2).top(28).background(ModularUIHelper.BACKGROUND_TEXTURE);

        var pages = new PagedWidget<>().controller(controller).fullWidth().height(100);

        // build each slot's module widget synchronously here (registering its sync handlers the normal way,
        // during panel construction) instead of via the DynamicSyncHandler's widgetProvider - creating new
        // sync handlers there only happens safely on genuine later rebuilds (e.g. a module swap), not on the
        // very first build, since that gets replayed from inside PanelSyncManager's own init pass and corrupts
        // its sync handler map
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            pages.addPage(
                    new DynamicSyncedWidget<>().fullWidth().height(100).syncHandler(moduleSyncHandlers.get(i))
                            .initialChild(addModuleUI(new Flow(GuiAxis.X).fullWidth().height(100), syncManager, i)));
        }

        mainPanel.child(pages);

        if (addPlayerInventory) mainPanel.child(SlotGroupWidget.playerInventory(true));

        return (ParentWidget) widget.child(mainPanel);
    }

    @Override
    public int getWidth() {
        return 254;
    }

    @Override
    public int getHeight() {
        return 210;
    }
}
