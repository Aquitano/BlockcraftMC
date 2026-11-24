package me.blockcraft.duell.game;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;
import java.util.UUID;

public interface DuelGame extends AutoCloseable {
    void activate();
    void respawn(PlayerRespawnEvent event);
    void spectate(Player player);
    int progress(UUID participant);
    Optional<UUID> timeoutWinner();
    @Override
    void close();
}
