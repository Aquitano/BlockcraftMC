package me.blockcraft.api.game;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.function.BooleanSupplier;

/**
 * What every arena forbids regardless of phase: mob spawns, weather, leaf decay, farmland trampling, and moving
 * while the round is frozen. Damage, hunger and inventory rules differ per game and stay with it.
 */
public final class ArenaProtection implements Listener {

    private final BooleanSupplier frozen;

    public ArenaProtection(BooleanSupplier frozen) {
        this.frozen = frozen;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onTrample(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (frozen.getAsBoolean() && event.hasChangedPosition()) {
            var to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
        }
    }
}
