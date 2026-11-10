package me.blockcraft.lobby.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Set;

/**
 * What the lobby keeps still beyond {@code WorldProtection}: no mob spawns by itself or hunts anyone, leaves stay and
 * the sky stays clear.
 */
public final class ProtectionListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> MANUAL_SPAWNS = Set.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM, CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!MANUAL_SPAWNS.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
            var world = event.getWorld();
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
        }
    }
}
