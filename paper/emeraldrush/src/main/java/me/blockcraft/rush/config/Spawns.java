package me.blockcraft.rush.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Spawn points persisted in the plugin's config.yml under their {@link SpawnPoint#key()}. */
public final class Spawns {

    private final JavaPlugin plugin;

    public Spawns(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<Location> get(SpawnPoint point) {
        var section = plugin.getConfig().getConfigurationSection(point.key());
        if (section == null) {
            return Optional.empty();
        }
        var world = Bukkit.getWorld(section.getString("World", ""));
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, section.getDouble("X"), section.getDouble("Y"), section.getDouble("Z"),
                (float) section.getDouble("Yaw"), (float) section.getDouble("Pitch")));
    }

    public void set(SpawnPoint point, Location location) {
        var config = plugin.getConfig();
        config.set(point.key() + ".X", location.getX());
        config.set(point.key() + ".Y", location.getY());
        config.set(point.key() + ".Z", location.getZ());
        config.set(point.key() + ".Yaw", location.getYaw());
        config.set(point.key() + ".Pitch", location.getPitch());
        config.set(point.key() + ".World", location.getWorld().getName());
        plugin.saveConfig();
    }

    public void teleport(Player player, SpawnPoint point) {
        get(point).ifPresent(player::teleport);
    }

    public SpawnPoint deathmatch(int index) {
        return SpawnPoint.DEATHMATCH.get(Math.floorMod(index, SpawnPoint.DEATHMATCH.size()));
    }

    public SpawnPoint randomDeathmatch() {
        return deathmatch(ThreadLocalRandom.current().nextInt(SpawnPoint.DEATHMATCH.size()));
    }
}
