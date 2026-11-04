package me.blockcraft.api.game;

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Below the void line a player of the running round dies, falling from whoever hit them last. A spectator, and after
 * the round anyone still in the arena, is put back at the spectators' spawn and may fly; an empty spawn puts nobody back.
 */
public final class VoidLine implements Listener {

    private final Round round;
    private final DoubleSupplier height;
    private final Supplier<Optional<Location>> spectatorSpawn;

    public VoidLine(Round round, DoubleSupplier height, Supplier<Optional<Location>> spectatorSpawn) {
        this.round = round;
        this.height = height;
        this.spectatorSpawn = spectatorSpawn;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        var phase = round.phase();
        if (phase == GamePhase.LOBBY || event.getTo().getY() >= height.getAsDouble()) {
            return;
        }
        var player = event.getPlayer();
        if (phase == GamePhase.INGAME && round.isPlaying(player)) {
            if (!player.isDead()) {
                player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
            }
        } else if (round.isSpectator(player) || phase == GamePhase.ENDING) {
            spectatorSpawn.get().filter(spawn -> spawn.getWorld().equals(player.getWorld())).ifPresent(spawn -> {
                player.teleport(spawn);
                player.setAllowFlight(true);
                player.setFlying(true);
            });
        }
    }
}
