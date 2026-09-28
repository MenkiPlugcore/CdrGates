package id.menkiplugcore.cdrgates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey from(Location location) {
        Objects.requireNonNull(location.getWorld(), "Location world cannot be null");
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public String encode() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static BlockKey decode(String encoded) {
        if (encoded == null) return null;
        String[] split = encoded.split(";", 4);
        if (split.length != 4) return null;
        try {
            return new BlockKey(split[0], Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
