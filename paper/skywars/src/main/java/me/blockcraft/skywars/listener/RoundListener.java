package me.blockcraft.skywars.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.skywars.game.SkyWarsGame;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * The round's rules besides the game's: in the cages nobody is hurt and nothing is used or placed; nobody builds
 * above the build height; below the void line a player falls to their death; enchanting tables bring their own lapis.
 */
public final class RoundListener implements Listener {

    private static final ItemStack LAPIS = ItemStack.of(Material.LAPIS_LAZULI, 64);
    private static final int LAPIS_SLOT = 1;

    private final SkyWarsGame game;
    private final Messages messages;

    public RoundListener(SkyWarsGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && game.frozen() && game.inArena(event.getEntity().getWorld())) {
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
    public void onMove(PlayerMoveEvent event) {
        if (game.isIngame() && game.inArena(event.getTo().getWorld()) && event.getTo().getY() < game.config().arena().voidHeight()) {
            game.fell(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantingOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof EnchantingInventory table) {
            table.setSecondary(LAPIS.clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantingClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() instanceof EnchantingInventory && event.getRawSlot() == LAPIS_SLOT) {
            event.setCancelled(true);
        }
    }

    /** The lapis belongs to the table, not to whoever closes it. */
    @EventHandler
    public void onEnchantingClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof EnchantingInventory table) {
            table.setSecondary(null);
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
