package me.blockcraft.quicksg.listener;

import me.blockcraft.api.config.Locations;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.game.Game;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathListener implements Listener {

    private final Game game;
    private final Locations<LocationKey> locations;

    public DeathListener(Game game, Locations<LocationKey> locations) {
        this.game = game;
        this.locations = locations;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        var victim = event.getPlayer();
        game.handleDeath(victim, victim.getKiller());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        locations.get(LocationKey.SPECTATOR).ifPresent(event::setRespawnLocation);
    }
}
