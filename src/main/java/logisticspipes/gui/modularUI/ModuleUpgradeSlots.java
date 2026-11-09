package logisticspipes.gui.modularUI;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import logisticspipes.LogisticsPipes;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.upgrades.IPipeUpgrade;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;

/**
 * The upgrade slots of one module, a column of {@link ModuleUpgradeManager#SLOTS}: in the chassis GUI next to each
 * module, and in the GUI of a module held in hand.
 * <p>
 * The slots ask for the module every time instead of keeping one: the chassis rebuilds a module page when the module in
 * its slot changes, and MUI hands the rebuilt page the sync handlers registered for the old module (same key). Slots
 * bound to the old module would show and hand out upgrades that already left with its item.
 */
public final class ModuleUpgradeSlots {

    public static final int WIDTH = 18;
    public static final int HEIGHT = 18 * ModuleUpgradeManager.SLOTS;
    // the frame around the column, the column sits PADDING in
    private static final int PADDING = 4;
    public static final int FRAME_WIDTH = WIDTH + 2 * PADDING;
    public static final int FRAME_HEIGHT = HEIGHT + 2 * PADDING;
    // between the window and the frame, and right of the frame
    public static final int GAP = 2;
    // what the frame adds to the width of a window
    public static final int SIDE_WIDTH = GAP + FRAME_WIDTH + GAP;
    private static final int GEAR_SIZE = 16;

    private ModuleUpgradeSlots() {}

    /**
     * Puts the upgrade column in its frame at left / top of the panel, and the settings gear centred below it (at the
     * top without a column). The same look in every window that has them.
     *
     * @param column the column from {@link #column}, null for none
     * @param gear   the settings button, null for none
     * @return the frame, null without a column
     */
    public static ParentWidget<?> addSide(ParentWidget<?> panel, int left, int top, ParentWidget<?> column,
            ButtonWidget<?> gear) {
        ParentWidget<?> frame = null;
        if (column != null) {
            frame = new ParentWidget<>().background(ModularUIHelper.BACKGROUND_TEXTURE).left(left).top(top)
                    .size(FRAME_WIDTH, FRAME_HEIGHT).child(column.pos(PADDING, PADDING));
            panel.child(frame);
        }
        if (gear != null) {
            panel.child(
                    gear.left(left + (FRAME_WIDTH - GEAR_SIZE) / 2)
                            .top(column != null ? top + FRAME_HEIGHT + PADDING : top));
        }
        return frame;
    }

    /**
     * Upgrade slots that take shift-clicked upgrades, for a GUI with one module.
     */
    public static ParentWidget<?> column(PanelSyncManager syncManager, String name, Supplier<LogisticsModule> module,
            Runnable onChange) {
        return column(syncManager, name, module, onChange, () -> true);
    }

    /**
     * @param name     unique per module place in one GUI: the sync handler and slot group name
     * @param module   the module the slots belong to right now, null if there is none (the slots take nothing then)
     * @param onChange run on the server after an upgrade was put in or taken out, may be null
     * @param shown    whether the player sees these slots now, on both sides: only shown slots take upgrades, so a
     *                 shift-click goes into the module the player looks at
     */
    public static ParentWidget<?> column(PanelSyncManager syncManager, String name, Supplier<LogisticsModule> module,
            Runnable onChange, BooleanSupplier shown) {
        syncManager.registerSlotGroup(new SlotGroup(name, 1, 100, true));
        Flow column = new Flow(GuiAxis.Y).width(WIDTH).height(HEIGHT);
        for (int i = 0; i < ModuleUpgradeManager.SLOTS; i++) {
            final int index = i;
            // registered explicitly: the chassis builds module pages in a DynamicSyncHandler, which allows no other way
            ItemSlotSH handler = syncManager.getOrCreateSyncHandler(
                    name,
                    index,
                    ItemSlotSH.class,
                    () -> new ItemSlotSH(createSlot(name, index, module, onChange, shown)));
            column.child(
                    new ItemSlot().syncHandler(handler)
                            .background(UITexture.fullImage(PipeGuiFactory.UpgradeSlotTexture)));
        }
        return column;
    }

    private static ModularSlot createSlot(String group, int index, Supplier<LogisticsModule> module, Runnable onChange,
            BooleanSupplier shown) {
        // one upgrade per slot: a module takes as many of one upgrade as it has slots. Both limits, MUI asks one or the
        // other depending on the click
        return new ModularSlot(new CurrentModuleUpgrades(module), index) {

            @Override
            public int getItemStackLimit(ItemStack stack) {
                return 1;
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        }.filter(stack -> shown.getAsBoolean() && isAllowed(stack, module.get())).slotGroup(group)
                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                    if (client || init) {
                        return;
                    }
                    LogisticsModule current = module.get();
                    if (current == null) {
                        return;
                    }
                    // the inventory wrapper doesn't tell the manager, so the upgrade effects are counted here
                    ModuleUpgradeManager upgrades = current.getModuleUpgrades();
                    upgrades.InventoryChanged(upgrades.getInv());
                    if (onChange != null) {
                        onChange.run();
                    }
                });
    }

    private static boolean isAllowed(ItemStack stack, LogisticsModule module) {
        if (module == null || !PipeGuiFactory.isUpgradeItem(stack)) {
            return false;
        }
        IPipeUpgrade upgrade = LogisticsPipes.UpgradeItem.getUpgradeForItem(stack, null);
        return upgrade != null && upgrade.isAllowedForModule(module);
    }

    /**
     * The upgrade inventory of whatever module is there right now; without a module an empty inventory nothing can be
     * put into (the filter refuses).
     */
    private static final class CurrentModuleUpgrades extends InvWrapper {

        private final Supplier<LogisticsModule> module;

        CurrentModuleUpgrades(Supplier<LogisticsModule> module) {
            super(new ModuleUpgradeManager().getInv());
            this.module = module;
        }

        @Override
        public IInventory getInv() {
            LogisticsModule current = module.get();
            // without a module a fresh empty inventory every time: an update the client gets before it knows the
            // module must not stay anywhere (a shared one showed it in every empty slot, on both sides in single
            // player)
            return current == null ? new ModuleUpgradeManager().getInv() : current.getModuleUpgrades().getInv();
        }
    }
}
