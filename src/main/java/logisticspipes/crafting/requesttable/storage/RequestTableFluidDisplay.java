package logisticspipes.crafting.requesttable.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.Loader;
import gregtech.api.util.GTUtility;
import logisticspipes.utils.FluidIdentifier;

/** Uses NEI's fluid display item while retaining the LP fluid identifier for packets and storage. */
public final class RequestTableFluidDisplay {

    private RequestTableFluidDisplay() {}

    public static ItemStack of(FluidStack fluid, int amount) {
        FluidStack display = fluid.copy();
        display.amount = Math.max(0, amount);
        return Loader.isModLoaded("gregtech") ? GregTechDisplay.create(display)
                : FluidIdentifier.get(display).getItemIdentifier().makeStack(display.amount).makeNormalStack();
    }

    /** Monitor cards display their quantities beside the icon. */
    public static ItemStack icon(FluidStack fluid) {
        return Loader.isModLoaded("gregtech") ? GregTechDisplay.icon(fluid)
                : FluidIdentifier.get(fluid).getItemIdentifier().makeStack(1).makeNormalStack();
    }

    /** Matches the amount text rendered by the NEI fluid display item, including its unit. */
    public static String formatAmount(int amount) {
        return Loader.isModLoaded("gregtech") ? GregTechDisplay.formatAmount(amount) : amount + " mB";
    }

    private static final class GregTechDisplay {

        private static String formatAmount(int amount) {
            if (amount < 10_000) return amount + "L";
            // NEI's GT renderer uses AE2's four-character wide format before appending L.
            // Match it locally: the formatter is not part of the AE2 API dependency.
            long divisor = 1_000;
            String suffix = "k";
            if (amount >= 1_000_000_000) {
                divisor = 1_000_000_000;
                suffix = "G";
            } else if (amount >= 1_000_000) {
                divisor = 1_000_000;
                suffix = "M";
            }
            long whole = amount / divisor;
            long tenth = amount / (divisor / 10) % 10;
            return whole + (whole < 10 && tenth > 0 ? "." + tenth : "") + suffix + "L";
        }

        private static ItemStack icon(FluidStack fluid) {
            return GTUtility.getFluidDisplayStack(fluid, false);
        }

        private static ItemStack create(FluidStack fluid) {
            return GTUtility.getFluidDisplayStack(fluid, true);
        }
    }
}
