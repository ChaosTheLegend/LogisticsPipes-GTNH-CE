package logisticspipes.crafting;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.github.bsideup.jabel.Desugar;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.items.LogisticsItem;
import logisticspipes.utils.string.ChatColor;

public class ItemMemoryChip extends LogisticsItem {

    private static final String PATTERN_SATELLITE_IDS_TAG = "patternSatelliteIds";
    private static final String PATTERN_SATELLITES_TAG = "patternSatelliteRefs";
    private static final String PATTERN_SATELLITE_MODE_TAG = "patternSatelliteMode";
    private static final String LAST_PATTERN_SATELLITE_ID_TAG = "lastPatternSatelliteId";
    private static final String LAST_PATTERN_SATELLITE_UUID_TAG = "lastPatternSatelliteUuid";
    private static final String LAST_PATTERN_SATELLITE_NAME_TAG = "lastPatternSatelliteName";

    public enum PatternSatelliteMode {
        FAVORITES,
        APPLY_LAST_TO_RECIPE
    }

    @Desugar
    public record StoredPatternSatellite(int id, String uuid, String name) {

        public StoredPatternSatellite {
            uuid = uuid == null ? "" : uuid;
            name = name == null ? "" : name;
        }
    }

    public static int[] getPatternSatelliteIds(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return new int[0];
        }
        return stack.getTagCompound().getIntArray(PATTERN_SATELLITE_IDS_TAG);
    }

    public static List<StoredPatternSatellite> getPatternSatellites(ItemStack stack) {
        TreeSet<Integer> legacyIds = new TreeSet<>();
        List<StoredPatternSatellite> result = new java.util.ArrayList<>();
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return result;
        }
        NBTTagList list = stack.getTagCompound().getTagList(PATTERN_SATELLITES_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int id = tag.getInteger("id");
            if (id > 0) {
                legacyIds.add(id);
                result.add(new StoredPatternSatellite(id, tag.getString("uuid"), tag.getString("name")));
            }
        }
        for (int id : getPatternSatelliteIds(stack)) {
            if (id > 0 && !legacyIds.contains(id)) {
                result.add(new StoredPatternSatellite(id, "", ""));
            }
        }
        return result;
    }

    public static PatternSatelliteMode getPatternSatelliteMode(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return PatternSatelliteMode.FAVORITES;
        }
        int mode = stack.getTagCompound().getInteger(PATTERN_SATELLITE_MODE_TAG);
        PatternSatelliteMode[] values = PatternSatelliteMode.values();
        return values[Math.max(0, Math.min(values.length - 1, mode))];
    }

    public static PatternSatelliteMode cyclePatternSatelliteMode(ItemStack stack) {
        PatternSatelliteMode[] values = PatternSatelliteMode.values();
        PatternSatelliteMode next = values[(getPatternSatelliteMode(stack).ordinal() + 1) % values.length];
        getOrCreateTag(stack).setInteger(PATTERN_SATELLITE_MODE_TAG, next.ordinal());
        return next;
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                PatternSatelliteMode mode = cyclePatternSatelliteMode(stack);
                player.addChatComponentMessage(new ChatComponentText("Memory chip mode: " + formatMode(mode)));
            }
            return stack;
        }
        return super.onItemRightClick(stack, world, player);
    }

    public static boolean addPatternSatellite(ItemStack stack, PipeItemsPatternSatelliteLogistics satellite) {
        if (satellite == null) {
            return false;
        }
        return addPatternSatellite(
                stack,
                satellite.satelliteId,
                satellite.getSatelliteUuid(),
                satellite.getDisplayName());
    }

    public static boolean addPatternSatellite(ItemStack stack, int satelliteId, String satelliteUuid,
            String satelliteName) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || satelliteId <= 0) {
            return false;
        }
        NBTTagCompound root = getOrCreateTag(stack);
        List<StoredPatternSatellite> satellites = getPatternSatellites(stack);
        boolean added = true;
        for (StoredPatternSatellite satellite : satellites) {
            if ((!satelliteUuid.isEmpty() && satelliteUuid.equals(satellite.uuid()))
                    || (satelliteUuid.isEmpty() && satellite.id() == satelliteId)) {
                added = false;
                break;
            }
        }
        if (added) {
            satellites.add(new StoredPatternSatellite(satelliteId, satelliteUuid, satelliteName));
        }
        writePatternSatellites(root, satellites);
        root.setInteger(LAST_PATTERN_SATELLITE_ID_TAG, satelliteId);
        root.setString(LAST_PATTERN_SATELLITE_UUID_TAG, satelliteUuid == null ? "" : satelliteUuid);
        root.setString(LAST_PATTERN_SATELLITE_NAME_TAG, satelliteName == null ? "" : satelliteName);
        return added;
    }

    public static boolean addPatternSatelliteId(ItemStack stack, int satelliteId) {
        return addPatternSatellite(stack, satelliteId, "", "");
    }

    public static int getLastPatternSatelliteId(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return 0;
        }
        return stack.getTagCompound().getInteger(LAST_PATTERN_SATELLITE_ID_TAG);
    }

    public static String getLastPatternSatelliteUuid(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return "";
        }
        return stack.getTagCompound().getString(LAST_PATTERN_SATELLITE_UUID_TAG);
    }

    public static String getLastPatternSatelliteName(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMemoryChip) || !stack.hasTagCompound()) {
            return "";
        }
        return stack.getTagCompound().getString(LAST_PATTERN_SATELLITE_NAME_TAG);
    }

    private static void writePatternSatellites(NBTTagCompound root, List<StoredPatternSatellite> satellites) {
        TreeSet<Integer> ids = new TreeSet<>();
        NBTTagList list = new NBTTagList();
        for (StoredPatternSatellite satellite : satellites) {
            if (satellite.id() <= 0) {
                continue;
            }
            ids.add(satellite.id());
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("id", satellite.id());
            tag.setString("uuid", satellite.uuid());
            tag.setString("name", satellite.name());
            list.appendTag(tag);
        }
        int[] storedIds = new int[ids.size()];
        int index = 0;
        for (Integer id : ids) {
            storedIds[index++] = id;
        }
        root.setIntArray(PATTERN_SATELLITE_IDS_TAG, storedIds);
        root.setTag(PATTERN_SATELLITES_TAG, list);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(ChatColor.AQUA + "Mode: " + ChatColor.WHITE + formatMode(getPatternSatelliteMode(stack)));
        List<StoredPatternSatellite> satellites = getPatternSatellites(stack);
        if (satellites.isEmpty()) {
            tooltip.add(ChatColor.GRAY + "No pattern satellites stored");
            return;
        }
        tooltip.add(ChatColor.AQUA + "Pattern satellites: " + ChatColor.WHITE + formatSatellites(satellites));
        String lastName = getLastPatternSatelliteName(stack);
        int lastId = getLastPatternSatelliteId(stack);
        if (lastId > 0) {
            tooltip.add(ChatColor.GRAY + "Last: " + (lastName == null || lastName.isEmpty() ? "#" + lastId : lastName));
        }
    }

    private static String formatSatellites(List<StoredPatternSatellite> satellites) {
        String[] names = new String[satellites.size()];
        for (int i = 0; i < satellites.size(); i++) {
            StoredPatternSatellite satellite = satellites.get(i);
            names[i] = satellite.name().isEmpty() ? "#" + satellite.id() : satellite.name();
        }
        return Arrays.toString(names);
    }

    private static String formatMode(PatternSatelliteMode mode) {
        return switch (mode) {
            case APPLY_LAST_TO_RECIPE -> "Apply last satellite to recipe";
            default -> "Favorites";
        };
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
