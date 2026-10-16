package logisticspipes.crafting;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.LogisticsSolidTileEntity;
import logisticspipes.blocks.crafting.AutoCraftingInventory;
import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.gui.modularUI.blocks.PatternCraftingTableMui;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.CraftingUtil;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.SimpleStackInventory;

public class PatternLogisticsCraftingTableTileEntity extends LogisticsSolidTileEntity
        implements IInventory, IGuiHolder<PosGuiData>, ISimpleInventoryEventHandler {

    private static final int INPUT_SIZE = 9;
    private static final int OUTPUT_SIZE = 3;
    private static final int UPGRADE_SIZE = 4;
    private static final int OUTPUT_START = INPUT_SIZE;
    private static final int BASE_COOLDOWN = 100;
    private static final int MIN_COOLDOWN = 1;

    private final SimpleStackInventory input = new SimpleStackInventory(INPUT_SIZE, "Pattern Crafting Input", 64);
    private final SimpleStackInventory output = new SimpleStackInventory(OUTPUT_SIZE, "Pattern Crafting Output", 64);
    private final SimpleStackInventory pendingOutput = new SimpleStackInventory(
            OUTPUT_SIZE,
            "Pattern Crafting Pending Output",
            64);
    private final SimpleStackInventory upgrades = new SimpleStackInventory(
            UPGRADE_SIZE,
            "Pattern Crafting Upgrades",
            20);

    private long craftStartedAt = -1;
    private long craftReadyAt = -1;
    private int craftCooldown = 0;
    private EntityPlayer fake;
    private boolean suppressRecipeCheck = false;
    private boolean inventoryCheckPending = false;

    public PatternLogisticsCraftingTableTileEntity() {
        input.addListener(this);
        output.addListener(this);
        upgrades.addListener(this);
    }

    public static boolean isSpeedUpgrade(ItemStack stack) {
        return stack != null && stack.getItem() == LogisticsPipes.UpgradeItem
                && stack.getItemDamage() == ItemUpgrade.SPEED;
    }

    public void onBlockBreak() {
        input.dropContents(worldObj, xCoord, yCoord, zCoord);
        output.dropContents(worldObj, xCoord, yCoord, zCoord);
        pendingOutput.dropContents(worldObj, xCoord, yCoord, zCoord);
        upgrades.dropContents(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        finishCraftIfReady();
        if (inventoryCheckPending && worldObj != null && !MainProxy.isClient(worldObj)) {
            inventoryCheckPending = false;
            onPlayerInventoryChanged();
        }
    }

    /**
     * Asks the table to re-check its inputs on its next tick; used by the GUI, whose slots don't always mark the
     * inventories dirty.
     */
    public void scheduleInventoryCheck() {
        inventoryCheckPending = true;
    }

    @Override
    public int getSizeInventory() {
        return INPUT_SIZE + OUTPUT_SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        finishCraftIfReady();
        if (slot < OUTPUT_START) {
            return input.getStackInSlot(slot);
        }
        return output.getStackInSlot(slot - OUTPUT_START);
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        finishCraftIfReady();
        if (slot < OUTPUT_START) {
            return null;
        }
        ItemStack stack = output.decrStackSize(slot - OUTPUT_START, count);
        finishPendingOutputIfPossible();
        tryStartCrafting();
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        finishCraftIfReady();
    }

    @Override
    public String getInventoryName() {
        return "PatternLogisticsCraftingTable";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        finishCraftIfReady();
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        clearInventory(input);
        clearInventory(output);
        clearInventory(pendingOutput);
        clearInventory(upgrades);
        input.readFromNBT(tag, "patternInput");
        output.readFromNBT(tag, "patternOutput");
        pendingOutput.readFromNBT(tag, "patternPendingOutput");
        upgrades.readFromNBT(tag, "patternUpgrades");
        craftStartedAt = tag.hasKey("craftStartedAt") ? tag.getLong("craftStartedAt") : -1;
        craftReadyAt = tag.hasKey("craftReadyAt") ? tag.getLong("craftReadyAt") : -1;
        craftCooldown = tag.getInteger("craftCooldown");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeCraftingPayload(tag);
    }

    private void writeCraftingPayload(NBTTagCompound tag) {
        input.writeToNBT(tag, "patternInput");
        output.writeToNBT(tag, "patternOutput");
        pendingOutput.writeToNBT(tag, "patternPendingOutput");
        upgrades.writeToNBT(tag, "patternUpgrades");
        tag.setLong("craftStartedAt", craftStartedAt);
        tag.setLong("craftReadyAt", craftReadyAt);
        tag.setInteger("craftCooldown", craftCooldown);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen("LogisticsPipes", mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return new PatternCraftingTableMui(this).buildUI(syncManager);
    }

    public SimpleStackInventory getInputInventory() {
        finishCraftIfReady();
        return input;
    }

    public SimpleStackInventory getOutputInventory() {
        finishCraftIfReady();
        return output;
    }

    public SimpleStackInventory getUpgradeInventory() {
        return upgrades;
    }

    public boolean canPlayerInsertInput(ItemStack stack) {
        return true;
    }

    public boolean isIdle() {
        finishCraftIfReady();
        return canAcceptInput() && isInputEmpty();
    }

    public void onPlayerInventoryChanged() {
        if (isClientSide()) {
            // the GUI's client copy of the inventories fires the listeners too; crafting there would desync it
            return;
        }
        finishCraftIfReady();
        tryStartCrafting();
        finishPendingOutputIfPossible();
        markDirty();
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        if (inventory == input || inventory == output || inventory == upgrades) {
            onPlayerInventoryChanged();
        }
    }

    public int roomForPatternPipeItem(ItemIdentifier item) {
        finishCraftIfReady();
        if (item == null || !canAcceptInput()) {
            return 0;
        }
        int room = 0;
        for (int slot = 0; slot < INPUT_SIZE; slot++) {
            room += roomForPatternPipeSlot(slot, item.makeNormalStack(1));
        }
        return room;
    }

    public int roomForPatternPipeSlot(int slot, ItemStack stack) {
        finishCraftIfReady();
        if (!canPatternPipeInsertIntoSlot(slot, stack)) {
            return 0;
        }
        ItemStack existing = input.getStackInSlot(slot);
        int stackLimit = Math.min(getInventoryStackLimit(), stack.getMaxStackSize());
        return existing == null ? stackLimit : stackLimit - existing.stackSize;
    }

    /**
     * Checks if the pattern pipe can insert into the slot. Returns true if the item stack can be inserted in the given
     * slot, or is the same item. Returns false if the slot is out of bounds, the given stack is null or the item is
     * different at the slot
     *
     * @param slot  the slot to look at
     * @param stack the stack that is tested for the insert
     * @return true if the stack can be inserted, otherwise false
     */
    public boolean canPatternPipeInsertIntoSlot(int slot, ItemStack stack) {
        if (stack == null || slot < 0 || slot >= INPUT_SIZE) return false;

        ItemStack existing = input.getStackInSlot(slot);
        return existing == null || ItemIdentifier.get(existing).equalsForCrafting(ItemIdentifier.get(stack));
    }

    public int insertFromPatternPipe(int slot, ItemStack stack) {
        finishCraftIfReady();
        if (!canPatternPipeInsertIntoSlot(slot, stack)) {
            return 0;
        }
        int amount = Math.min(stack.stackSize, roomForPatternPipeSlot(slot, stack));
        if (amount <= 0) {
            return 0;
        }
        ItemStack existing = input.getStackInSlot(slot);
        if (existing == null) {
            ItemStack inserted = stack.copy();
            inserted.stackSize = amount;
            input.setInventorySlotContents(slot, inserted);
        } else {
            existing.stackSize += amount;
            input.setInventorySlotContents(slot, existing);
        }
        if (!suppressRecipeCheck) {
            tryStartCrafting();
            markDirty();
        }
        return amount;
    }

    public int insertFromPatternPipe(ItemStack stack) {
        finishCraftIfReady();
        if (stack == null || !canAcceptInput()) {
            return 0;
        }
        int inserted = 0;
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < INPUT_SIZE && remaining.stackSize > 0; slot++) {
            int moved = insertFromPatternPipe(slot, remaining);
            inserted += moved;
            remaining.stackSize -= moved;
        }
        return inserted;
    }

    /**
     * Inserts a set from a pattern pipe into the input slots, all or nothing: nothing goes in unless every assignment
     * fits, so the table never starts a recipe from part of a set. Returns the inserted amount per assignment.
     */
    public int[] insertPatternPlanFromPatternPipe(List<PatternIngredientAssignment> assignments) {
        finishCraftIfReady();
        int[] inserted = new int[assignments == null ? 0 : assignments.size()];
        if (assignments == null || assignments.isEmpty() || !canAcceptInput()) {
            return inserted;
        }
        for (PatternIngredientAssignment assignment : assignments) {
            ItemStack stack = assignment.stack().makePatternStack();
            if (stack == null || roomForPatternPipeSlot(assignment.inputSlot(), stack) < stack.stackSize) {
                return inserted;
            }
        }
        suppressRecipeCheck = true;
        try {
            for (int i = 0; i < assignments.size(); i++) {
                PatternIngredientAssignment assignment = assignments.get(i);
                inserted[i] = insertFromPatternPipe(assignment.inputSlot(), assignment.stack().makePatternStack());
            }
        } finally {
            suppressRecipeCheck = false;
        }
        tryStartCrafting();
        markDirty();
        return inserted;
    }

    public boolean insertPatternFromPatternPipe(ItemStack pattern, int sets) {
        finishCraftIfReady();
        if (pattern == null || sets <= 0) {
            return false;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            ItemStack ingredient = configuredPattern.getStackInSlot(slot);
            if (ingredient == null) {
                continue;
            }
            if (roomForPatternPipeSlot(slot, ingredient) < ingredient.stackSize * sets) {
                return false;
            }
        }

        suppressRecipeCheck = true;
        boolean insertedAll = true;
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            ItemStack ingredient = configuredPattern.getStackInSlot(slot);
            if (ingredient != null) {
                ItemStack stack = ingredient.copy();
                stack.stackSize = ingredient.stackSize * sets;
                if (insertFromPatternPipe(slot, stack) != stack.stackSize) {
                    insertedAll = false;
                }
            }
        }

        suppressRecipeCheck = false;
        tryStartCrafting();
        markDirty();
        return insertedAll;
    }

    public ItemStack extractOutput(IResource wanted, int count) {
        finishCraftIfReady();
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (stack != null && wanted.matches(ItemIdentifier.get(stack), IResource.MatchSettings.NORMAL)) {
                ItemStack extracted = output.decrStackSize(i, Math.min(count, stack.stackSize));
                finishPendingOutputIfPossible();
                tryStartCrafting();
                return extracted;
            }
        }
        return null;
    }

    /**
     * Progress of the running craft from 0 to 1. Only meaningful on the server; the GUI syncs it.
     */
    public double getProgress() {
        if (craftStartedAt < 0 || craftReadyAt <= craftStartedAt || worldObj == null) {
            return 0;
        }
        long elapsed = Math.max(0, worldObj.getTotalWorldTime() - craftStartedAt);
        return Math.min(1D, (double) elapsed / (craftReadyAt - craftStartedAt));
    }

    public int getSpeedUpgradeCount() {
        int count = 0;
        for (int i = 0; i < upgrades.getSizeInventory(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (isSpeedUpgrade(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void tryStartCrafting() {
        if (isClientSide() || craftReadyAt >= 0 || hasPendingOutput()) {
            return;
        }
        if (fake == null) {
            fake = MainProxy.getFakePlayer(this);
        }
        if (!consumeCraftableInputsToPendingOutput()) {
            return;
        }
        craftCooldown = getReducedCooldown();
        craftStartedAt = worldObj != null ? worldObj.getTotalWorldTime() : 0;
        craftReadyAt = craftStartedAt + craftCooldown;
        markDirty();
    }

    private int getReducedCooldown() {
        return Math.max(MIN_COOLDOWN, BASE_COOLDOWN / (1 + getSpeedUpgradeCount()));
    }

    private void finishCraftIfReady() {
        if (worldObj == null || MainProxy.isClient(worldObj)
                || craftReadyAt < 0
                || worldObj.getTotalWorldTime() < craftReadyAt) {
            return;
        }
        craftReadyAt = -1;
        craftStartedAt = -1;
        craftCooldown = 0;
        finishPendingOutputIfPossible();
        tryStartCrafting();
        markDirty();
    }

    private boolean consumeCraftableInputsToPendingOutput() {
        // try to find a recipe
        AutoCraftingInventory craftingInventory = createSingleItemCraftingInventory();
        IRecipe recipe = findRecipe(craftingInventory);
        if (recipe == null) return false;

        // if we have one, copy a single instance into
        ItemStack result = recipe.getCraftingResult(craftingInventory);
        if (!canFitPendingOutput(result)) return false;

        consumeCraftingInputs(craftingInventory);
        SlotCrafting craftingSlot = new SlotCrafting(fake, craftingInventory, output, 0, 0, 0);
        craftingSlot.onPickupFromSlot(fake, result.copy());
        moveRemainingInputs(craftingInventory);
        addToInventory(pendingOutput, result.copy());

        return true;
    }

    private AutoCraftingInventory createPreviewInventory() {
        AutoCraftingInventory inventory = new AutoCraftingInventory(null);
        for (int i = 0; i < INPUT_SIZE; i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack != null) {
                ItemStack copy = stack.copy();
                copy.stackSize = 1;
                inventory.setInventorySlotContents(i, copy);
            }
        }
        return inventory;
    }

    private AutoCraftingInventory createSingleItemCraftingInventory() {
        AutoCraftingInventory inventory = new AutoCraftingInventory(null);
        for (int i = 0; i < INPUT_SIZE; i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack != null) {
                ItemStack single = stack.copy();
                single.stackSize = 1;
                inventory.setInventorySlotContents(i, single);
            }
        }
        return inventory;
    }

    private void consumeCraftingInputs(AutoCraftingInventory craftingInventory) {
        for (int i = 0; i < INPUT_SIZE; i++) {
            if (craftingInventory.getStackInSlot(i) != null) {
                input.decrStackSize(i, 1);
            }
        }
    }

    private IRecipe findRecipe(AutoCraftingInventory inventory) {
        for (IRecipe recipe : CraftingUtil.getRecipeList()) {
            if (recipe.matches(inventory, worldObj)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canFitPendingOutput(ItemStack result) {
        return canFitInventory(pendingOutput, result);
    }

    private boolean canFitOutput(ItemStack result) {
        return canFitInventory(output, result);
    }

    private boolean canFitInventory(SimpleStackInventory inventory, ItemStack result) {
        if (result == null) {
            return false;
        }
        int remaining = result.stackSize;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing == null) {
                remaining -= Math.min(remaining, result.getMaxStackSize());
            } else if (ItemIdentifier.get(existing).equalsForCrafting(ItemIdentifier.get(result))) {
                remaining -= Math.min(
                        remaining,
                        Math.min(existing.getMaxStackSize(), inventory.getInventoryStackLimit()) - existing.stackSize);
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean addToInventory(SimpleStackInventory inventory, ItemStack stack) {
        if (!canFitInventory(inventory, stack)) {
            return false;
        }
        int remaining = stack.stackSize;
        for (int i = 0; i < inventory.getSizeInventory() && remaining > 0; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing == null) {
                ItemStack inserted = stack.copy();
                inserted.stackSize = Math.min(remaining, inserted.getMaxStackSize());
                inventory.setInventorySlotContents(i, inserted);
                remaining -= inserted.stackSize;
            } else if (ItemIdentifier.get(existing).equalsForCrafting(ItemIdentifier.get(stack))) {
                int moved = Math.min(
                        remaining,
                        Math.min(existing.getMaxStackSize(), inventory.getInventoryStackLimit()) - existing.stackSize);
                if (moved > 0) {
                    existing.stackSize += moved;
                    inventory.setInventorySlotContents(i, existing);
                    remaining -= moved;
                }
            }
        }
        return remaining == 0;
    }

    private void finishPendingOutputIfPossible() {
        if (isCraftCoolingDown()) {
            return;
        }
        for (int i = 0; i < pendingOutput.getSizeInventory(); i++) {
            ItemStack stack = pendingOutput.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            if (!canFitOutput(stack)) {
                return;
            }
        }
        for (int i = 0; i < pendingOutput.getSizeInventory(); i++) {
            ItemStack stack = pendingOutput.getStackInSlot(i);
            if (stack != null) {
                addToInventory(output, stack);
                pendingOutput.setInventorySlotContents(i, null);
            }
        }
    }

    private boolean canAcceptInput() {
        return !isCraftCoolingDown() && !hasPendingOutput() && !hasOutput();
    }

    private boolean hasPendingOutput() {
        for (int i = 0; i < pendingOutput.getSizeInventory(); i++) {
            if (pendingOutput.getStackInSlot(i) != null) {
                return true;
            }
        }
        return false;
    }

    private void moveRemainingInputs(AutoCraftingInventory craftingInventory) {
        for (int i = 0; i < INPUT_SIZE; i++) {
            ItemStack stack = craftingInventory.getStackInSlot(i);
            craftingInventory.setInventorySlotContents(i, null);
            if (stack != null) {
                ItemIdentifierInventory.dropItems(worldObj, stack, xCoord, yCoord, zCoord);
            }
        }
        if (fake != null) {
            for (int i = 0; i < fake.inventory.getSizeInventory(); i++) {
                ItemStack stack = fake.inventory.getStackInSlot(i);
                fake.inventory.setInventorySlotContents(i, null);
                if (stack != null) {
                    ItemIdentifierInventory.dropItems(worldObj, stack, xCoord, yCoord, zCoord);
                }
            }
        }
    }

    public boolean isInputEmpty() {
        for (int i = 0; i < INPUT_SIZE; i++) {
            if (input.getStackInSlot(i) != null) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOutput() {
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            if (output.getStackInSlot(i) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isCraftCoolingDown() {
        return craftReadyAt >= 0;
    }

    private boolean isClientSide() {
        return worldObj == null || MainProxy.isClient(worldObj);
    }

    private void clearInventory(SimpleStackInventory inventory) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            inventory.setInventorySlotContents(i, null);
        }
    }
}
