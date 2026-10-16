package logisticspipes.crafting;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import lombok.Getter;

@Getter
public final class PatternSatelliteInfo {

    private final SatelliteType type;

    private final int id;
    private final int x;
    private final int y;
    private final int z;
    private final int dimension;
    private final int distance;
    private final boolean favorite;
    private final String uuid;
    private final String displayName;

    public PatternSatelliteInfo(int id, int x, int y, int z, int dimension, int distance, boolean favorite, String uuid,
            String displayName) {
        this(id, x, y, z, dimension, distance, favorite, uuid, displayName, SatelliteType.ITEM);
    }

    public PatternSatelliteInfo(int id, int x, int y, int z, int dimension, int distance, boolean favorite, String uuid,
            String displayName, SatelliteType type) {
        uuid = uuid == null ? "" : uuid;
        displayName = displayName == null || displayName.trim().isEmpty() ? Integer.toString(id) : displayName.trim();
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.distance = distance;
        this.favorite = favorite;
        this.uuid = uuid;
        this.displayName = displayName;
        this.type = type == null ? SatelliteType.ITEM : type;
    }

    public static PatternSatelliteInfo readData(LPDataInputStream data) throws IOException {
        return new PatternSatelliteInfo(
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readBoolean(),
                data.readUTF(),
                data.readUTF(),
                data.readEnum(SatelliteType.class));
    }

    public String getSearchText() {
        return (displayName + " #"
                + id
                + " s"
                + id
                + " satellite "
                + id
                + " "
                + uuid
                + " d"
                + dimension
                + " dim "
                + dimension
                + " "
                + x
                + " "
                + y
                + " "
                + z
                + " "
                + x
                + ","
                + y
                + ","
                + z
                + " "
                + type.name().toLowerCase(Locale.ROOT)
                + (favorite ? " favorite chip memory" : "")).toLowerCase(Locale.ROOT);
    }

    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(id);
        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);
        data.writeInt(dimension);
        data.writeInt(distance);
        data.writeBoolean(favorite);
        data.writeUTF(uuid);
        data.writeUTF(displayName);
        data.writeEnum(type);
    }

    public SatelliteType type() {
        return type;
    }

    public int id() {
        return id;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int dimension() {
        return dimension;
    }

    public int distance() {
        return distance;
    }

    public boolean favorite() {
        return favorite;
    }

    public String uuid() {
        return uuid;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PatternSatelliteInfo) obj;
        return this.id == that.id && this.x == that.x
                && this.y == that.y
                && this.z == that.z
                && this.dimension == that.dimension
                && this.distance == that.distance
                && this.favorite == that.favorite
                && Objects.equals(this.uuid, that.uuid)
                && Objects.equals(this.displayName, that.displayName)
                && this.type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, x, y, z, dimension, distance, favorite, uuid, displayName, type);
    }

    @Override
    public String toString() {
        return "PatternSatelliteInfo[" + "id="
                + id
                + ", "
                + "x="
                + x
                + ", "
                + "y="
                + y
                + ", "
                + "z="
                + z
                + ", "
                + "dimension="
                + dimension
                + ", "
                + "distance="
                + distance
                + ", "
                + "favorite="
                + favorite
                + ", "
                + "uuid="
                + uuid
                + ", "
                + "displayName="
                + displayName
                + ", "
                + "type="
                + type
                + ']';
    }

    public enum SatelliteType {
        ITEM,
        FLUID
    }

}
