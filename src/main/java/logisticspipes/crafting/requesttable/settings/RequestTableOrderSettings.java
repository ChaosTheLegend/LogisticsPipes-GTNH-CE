package logisticspipes.crafting.requesttable.settings;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import com.github.bsideup.jabel.Desugar;

import lombok.NonNull;
import lombok.Value;

/** Immutable, player-wide preferences for the request table's +/- order buttons. */
@Value
public class RequestTableOrderSettings {

    private static final String PLAYER_KEY = "logisticspipesRequestTableOrderSteps";
    private static final AmountSteps DEFAULT_STEPS = new AmountSteps(1, 10, 100, 1000);
    public static final RequestTableOrderSettings DEFAULT = new RequestTableOrderSettings(DEFAULT_STEPS, DEFAULT_STEPS);

    @NonNull
    AmountSteps itemSteps;
    @NonNull
    AmountSteps fluidSteps;

    public int[] getAmountSteps(boolean fluid) {
        return (fluid ? fluidSteps : itemSteps).toArray();
    }

    public static RequestTableOrderSettings readFromNBT(NBTTagCompound tag) {
        return new RequestTableOrderSettings(readSteps(tag, "items"), readSteps(tag, "fluids"));
    }

    private static AmountSteps readSteps(NBTTagCompound tag, String key) {
        int[] saved = tag.getIntArray(key);
        int[] values = DEFAULT_STEPS.toArray();
        for (int i = 0; i < Math.min(values.length, saved.length); i++) {
            if (saved[i] > 0) values[i] = saved[i];
        }
        return new AmountSteps(values[0], values[1], values[2], values[3]);
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setIntArray("items", itemSteps.toArray());
        tag.setIntArray("fluids", fluidSteps.toArray());
    }

    public static RequestTableOrderSettings forPlayer(EntityPlayer player) {
        return readFromNBT(
                player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(PLAYER_KEY));
    }

    /** Forge copies PlayerPersisted when a player respawns, and saves it with the player on logout. */
    public void saveForPlayer(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        persisted.setTag(PLAYER_KEY, tag);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    @Desugar
    public record AmountSteps(int first, int second, int third, int fourth) {

        public AmountSteps {
            if (first <= 0 || second <= 0 || third <= 0 || fourth <= 0) {
                throw new IllegalArgumentException("Order steps must be positive.");
            }
        }

        public int[] toArray() {
            return new int[] { first, second, third, fourth };
        }
    }
}
