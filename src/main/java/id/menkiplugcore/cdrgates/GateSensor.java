package id.menkiplugcore.cdrgates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record GateSensor(int id, String world, double x, double y, double z, double radius) {
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }
}
