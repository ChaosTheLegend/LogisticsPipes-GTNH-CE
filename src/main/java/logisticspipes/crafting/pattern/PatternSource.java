package logisticspipes.crafting.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;

/**
 * Where an edited pattern lives: a pattern slot of a pattern crafting pipe, or the pattern item a player holds.
 * <p>
 * The pattern is always the NBT of the pattern item, so a pattern edited by hand works in a pipe exactly like one
 * edited in the pipe. Stacks are read live on every call, never cached.
 */
public interface PatternSource {

    /**
     * Number of pattern slots; the editor selects one of them.
     */
    int getPatternCount();

    /**
     * The pattern item in the slot, or null when the slot holds no pattern.
     */
    ItemStack getPatternStack(int slot);

    /**
     * Called after the pattern NBT in the slot was changed.
     */
    void markPatternChanged(int slot);

    /**
     * Called after an input of the pattern in the slot got a satellite target.
     */
    default void onSatelliteAssigned(int satelliteId, String satelliteUuid, boolean fluid) {}

    static boolean isPattern(ItemStack stack) {
        return stack != null && stack.getItem() == LogisticsPipes.LogisticsPattern;
    }

    static PatternSource of(PipeItemsPatternCraftingLogistics pipe) {
        return new PatternSource() {

            @Override
            public int getPatternCount() {
                return 9;
            }

            @Override
            public ItemStack getPatternStack(int slot) {
                return pipe.getPatternModule().getPatternItemStack(slot);
            }

            @Override
            public void markPatternChanged(int slot) {
                pipe.getPatternModule().markPatternInventoryDirty();
                if (pipe.container != null) {
                    pipe.container.markDirty();
                }
            }

            @Override
            public void onSatelliteAssigned(int satelliteId, String satelliteUuid, boolean fluid) {
                // link it so staged ingredient requests can resolve the satellite
                if (fluid) {
                    pipe.linkPatternFluidSatellite(satelliteId, satelliteUuid);
                } else {
                    pipe.linkPatternSatellite(satelliteId, satelliteUuid);
                }
            }
        };
    }

    /**
     * The pattern in the given slot of the player's main inventory. A pipe links the satellites of a hand-edited
     * pattern when it resolves them, so nothing is linked here.
     */
    static PatternSource heldBy(EntityPlayer player, int inventorySlot) {
        return new PatternSource() {

            @Override
            public int getPatternCount() {
                return 1;
            }

            @Override
            public ItemStack getPatternStack(int slot) {
                if (slot != 0 || inventorySlot < 0 || inventorySlot >= player.inventory.mainInventory.length) {
                    return null;
                }
                ItemStack stack = player.inventory.mainInventory[inventorySlot];
                return isPattern(stack) ? stack : null;
            }

            @Override
            public void markPatternChanged(int slot) {
                player.inventory.markDirty();
            }
        };
    }
}
