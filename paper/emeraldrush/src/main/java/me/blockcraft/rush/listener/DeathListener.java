package me.blockcraft.rush.listener;

import me.blockcraft.rush.game.Game;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;

public final class DeathListener implements Listener {

    private final Game game;

    public DeathListener(Game game) {
        this.game = game;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        event.getDrops().clear();
        var victim = event.getEntity();
        game.handleDeath(victim, Optional.ofNullable(victim.getKiller()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        game.respawn(event);
    }
}
