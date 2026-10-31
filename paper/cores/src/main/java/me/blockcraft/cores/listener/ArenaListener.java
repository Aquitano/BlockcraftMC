package me.blockcraft.cores.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.common.message.Messages;
import me.blockcraft.cores.game.CoresGame;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The round's rules for the map: the bases keep their blocks except the iron, a core's cube and a spawn take no
 * block, and everything else breaks and builds inside the border. Enemies break cores; teammates do not hurt each
 * other, and nobody hurts a player who just respawned. The enchanting tables come with lapis.
 */
public final class ArenaListener implements Listener {

    private static final ItemStack LAPIS = ItemStack.of(Material.LAPIS_LAZULI, 64);
    private static final int LAPIS_SLOT = 1;

    private final CoresGame game;
    private final Messages messages;

    public ArenaListener(CoresGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!inRound()) {
            return;
        }
        var block = event.getBlock();
        var core = game.coreAt(block);
        if (core.isPresent()) {
            game.breakCore(event, core.get());
        } else if (game.map().isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (inRound() && !game.map().mayBuild(event.getBlockPlaced(), game.buildHeight())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("build.outside"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (inRound() && !game.map().mayBuild(event.getBlock(), game.buildHeight())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("build.outside"));
        }
    }

    /** Nothing flows into a core's cube or onto a spawn. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (inRound() && !game.map().mayBuild(event.getToBlock(), game.buildHeight())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (game.map().isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        keepBases(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        keepBases(event.blockList());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (inRound() && event.getTo().getY() < game.voidHeight()) {
            game.fell(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var damager = event.getDamager();
        var attacker = switch (damager) {
            case Player player -> player;
            case Projectile projectile when projectile.getShooter() instanceof Player shooter -> shooter;
            default -> event.getDamageSource().getCausingEntity() instanceof Player causing ? causing : null;
        };
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (game.sameTeam(attacker, victim) || game.isSpawnProtected(victim)) {
            event.setCancelled(true);
            if (damager instanceof Projectile) {
                damager.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLandedHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamageSource().getCausingEntity() instanceof Player attacker
                && !attacker.equals(victim)) {
            game.endSpawnProtection(attacker);
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

    private void keepBases(List<Block> blocks) {
        blocks.removeIf(block -> game.map().isProtected(block) || game.coreAt(block).isPresent());
    }

    private boolean inRound() {
        return game.phase() == GamePhase.INGAME;
    }
}
