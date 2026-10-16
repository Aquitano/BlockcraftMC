package me.blockcraft.lobby.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.activity.Parkour;
import me.blockcraft.lobby.config.LobbyConfig;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;


public final class DoubleJumpListener implements Listener {

    private final LobbyConfig config;
    private final Parkour parkour;

    public DoubleJumpListener(LobbyConfig config, Parkour parkour) {
        this.config = config;
        this.parkour = parkour;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (parkour.running(player) || parkour.startsAt(event.getTo())) {
            return;
        }
        if (canDoubleJump(player) && player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR) {
            player.setAllowFlight(true);
        }
        if (player.getLocation().getBlock().getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            launch(player);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (parkour.running(player)) {
            event.setCancelled(true);
            return;
        }
        if (!canDoubleJump(player)) {
            return;
        }
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setVelocity(player.getLocation().getDirection().multiply(config.doubleJumpMultiply()).setY(config.doubleJumpHeight()));
        player.setFallDistance(0.0f);
        Sounds.DOUBLE_JUMP.play(player);
    }

    private void launch(Player player) {
        Vector velocity = player.getLocation().getDirection().setY(config.jumpPadY()).multiply(config.jumpPadMultiply());
        if (velocity.length() > config.jumpPadMaxLength()) {
            return;
        }
        Sounds.JUMP_PAD.play(player);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation(), 10, 0.3, 0.3, 0.3, 0.1);
        player.setVelocity(velocity);
    }

    private static boolean canDoubleJump(Player player) {
        return player.hasPermission(LobbyPermissions.DOUBLE_JUMP) && player.getGameMode() != GameMode.CREATIVE
                && !BlockcraftApi.get().flight().granted(player);
    }
}
