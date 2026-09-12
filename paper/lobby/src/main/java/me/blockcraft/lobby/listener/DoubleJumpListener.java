package me.blockcraft.lobby.listener;

import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.config.LobbyConfig;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

public final class DoubleJumpListener implements Listener {

    private final LobbyConfig config;

    public DoubleJumpListener(LobbyConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
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
        if (!canDoubleJump(player)) {
            return;
        }
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setVelocity(player.getLocation().getDirection().multiply(config.doubleJumpMultiply()).setY(config.doubleJumpHeight()));
        player.setFallDistance(0.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 4.0f, 4.0f);
    }

    private void launch(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, 1.0f, 0.0f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation(), 10, 0.3, 0.3, 0.3, 0.1);
        Vector velocity = player.getLocation().getDirection().setY(config.jumpPadY()).multiply(config.jumpPadMultiply());
        if (velocity.length() <= config.jumpPadMaxLength()) {
            player.setVelocity(velocity);
        }
    }

    private static boolean canDoubleJump(Player player) {
        return player.hasPermission(LobbyPermissions.DOUBLE_JUMP) && player.getGameMode() != GameMode.CREATIVE;
    }
}
