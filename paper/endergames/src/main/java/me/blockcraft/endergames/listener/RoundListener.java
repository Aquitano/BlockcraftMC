package me.blockcraft.endergames.listener;

import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.LootChest;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.endergames.game.EnderGamesGame;
import me.blockcraft.endergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The round's rules besides the kits': nobody is hurt while the players are frozen, during the protection time and
 * after the round; the wandering ender chests open their loot and cannot be broken or blown up; placed TNT lights
 * itself; enchanting tables bring their own lapis; portals lead nowhere; compasses track.
 */
public final class RoundListener implements Listener {

    private static final ItemStack LAPIS = ItemStack.of(Material.LAPIS_LAZULI, 64);
    private static final int LAPIS_SLOT = 1;
    private static final int TNT_FUSE_TICKS = 30;

    private final EnderGamesGame game;
    private final CompassTracker compass;

    public RoundListener(EnderGamesGame game, CompassTracker compass) {
        this.game = game;
        this.compass = compass;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && game.world().contains(event.getEntity().getWorld()) && game.peaceful()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (game.frozen() && game.isPlaying(event.getPlayer()) && event.hasChangedPosition()) {
            var to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
        }
    }

    /** Frozen players keep their hands still too: no pearl, hammer or TNT away from the spawn before the start. */
    @EventHandler(priority = EventPriority.LOW)
    public void onFrozenInteract(PlayerInteractEvent event) {
        if (game.frozen() && game.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFrozenPlace(BlockPlaceEvent event) {
        if (game.frozen() && game.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        var player = event.getPlayer();
        var block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && block.getType() == Material.ENDER_CHEST
                && game.world().contains(block.getWorld())) {
            event.setCancelled(true);
            game.openEnderChest(player, block).ifPresent(loot -> {
                Sounds.MENU_OPEN.play(player);
                player.openInventory(loot);
            });
            return;
        }
        var item = event.getItem();
        if (item != null && item.getType() == Material.COMPASS && game.isIngame() && game.isPlaying(player)) {
            event.setCancelled(true);
            if (game.kit(player) == Kit.TRACKER) {
                game.abilities().track(player);
            } else {
                compass.track(player, game.fighting());
            }
        }
    }

    @EventHandler
    public void onLootClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof LootChest && event.getPlayer() instanceof Player player) {
            Sounds.MENU_CLICK.play(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if (block.getType() != Material.ENDER_CHEST || !game.world().contains(block.getWorld())) {
            return;
        }
        if (game.isEnderChest(block)) {
            event.setCancelled(true);
        } else {
            event.setDropItems(false);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(game::isEnderChest);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(game::isEnderChest);
    }

    /** TNT goes off by itself once placed, as in GommeHD's round. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        if (block.getType() != Material.TNT || !game.isIngame() || !game.isPlaying(event.getPlayer())) {
            return;
        }
        block.setType(Material.AIR);
        block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(TNT_FUSE_TICKS);
            tnt.setSource(event.getPlayer());
        });
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

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        game.death(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        game.respawn(event);
    }
}
