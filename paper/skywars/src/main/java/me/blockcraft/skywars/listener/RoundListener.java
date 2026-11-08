package me.blockcraft.skywars.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.common.message.Messages;
import me.blockcraft.skywars.game.SkyWarsGame;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * The round's rules besides the game's: in the cages nobody is hurt and nothing is used or placed; nobody builds
 * above the build height.
 */
public final class RoundListener implements Listener {

    private final SkyWarsGame game;
    private final Messages messages;

    public RoundListener(SkyWarsGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && (game.frozen() || game.phase() == GamePhase.ENDING)
                && game.inArena(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Frozen players keep their hands still: no pearl, bow or potion out of the cage before the start. */
    @EventHandler(priority = EventPriority.LOW)
    public void onFrozenInteract(PlayerInteractEvent event) {
        if (game.frozen() && game.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!game.isIngame()) {
            return;
        }
        if (game.frozen() && game.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        } else if (tooHigh(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("build.too-high"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (game.isIngame() && tooHigh(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("build.too-high"));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        game.death(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        game.respawn(event);
    }

    private boolean tooHigh(Block block) {
        return block.getY() > game.config().arena().buildHeight();
    }
}
