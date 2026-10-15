package logisticspipes.crafting.requesttable.mk3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Persists display settings by player UUID inside a single request table.
 */
public class RequestTableMk3DisplaySettingsStore {

    private static final int TAG_COMPOUND = 10;
    private static final String NBT_PLAYER = "player";
    private static final String NBT_SETTINGS = "settings";

    private final Map<UUID, RequestTableMk3DisplaySettings> settingsByPlayer = new HashMap<>();

    public RequestTableMk3DisplaySettings get(UUID playerId) {
        RequestTableMk3DisplaySettings settings = settingsByPlayer.get(playerId);
        return settings == null ? RequestTableMk3DisplaySettings.DEFAULT : settings;
    }

    /**
     * @return {@code true} if the persisted state changed
     */
    public boolean set(UUID playerId, RequestTableMk3DisplaySettings settings) {
        RequestTableMk3DisplaySettings previous;
        if (RequestTableMk3DisplaySettings.DEFAULT.equals(settings)) {
            previous = settingsByPlayer.remove(playerId);
            return previous != null;
        }
        previous = settingsByPlayer.put(playerId, settings);
        return !settings.equals(previous);
    }

    public void readFromNBT(NBTTagCompound root, String key) {
        settingsByPlayer.clear();
        NBTTagList list = root.getTagList(key, TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            try {
                UUID playerId = UUID.fromString(entry.getString(NBT_PLAYER));
                RequestTableMk3DisplaySettings settings = RequestTableMk3DisplaySettings
                        .readFromNBT(entry.getCompoundTag(NBT_SETTINGS));
                if (!RequestTableMk3DisplaySettings.DEFAULT.equals(settings)) {
                    settingsByPlayer.put(playerId, settings);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries while retaining all valid player settings.
            }
        }
    }

    public void writeToNBT(NBTTagCompound root, String key) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, RequestTableMk3DisplaySettings> stored : settingsByPlayer.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(NBT_PLAYER, stored.getKey().toString());
            NBTTagCompound settings = new NBTTagCompound();
            stored.getValue().writeToNBT(settings);
            entry.setTag(NBT_SETTINGS, settings);
            list.appendTag(entry);
        }
        root.setTag(key, list);
    }
}
