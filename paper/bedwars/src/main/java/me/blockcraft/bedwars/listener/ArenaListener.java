package me.blockcraft.bedwars.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.game.Game;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The round's rules for the map: players build inside its border and break only what players placed, grass,
 * flowers and the beds of other teams; TNT goes off when placed and takes only placed blocks with it; a cobweb
 * melts after a while unless it guards a bed. Falling below the void line kills, and teammates do not hurt each
 * other, with arrows neither.
 */
public final class ArenaListener implements Listener {

    /** Within two blocks of a standing bed a cobweb is part of its defence. */
    private static final double BED_GUARD_SQUARED = 4.001;

    private final Plugin plugin;
    private final Game game;
    private final Messages messages;
    private final BedWarsConfig config;
    private final Set<Block> placed = new HashSet<>();
    private final Map<UUID, Long> lastWeb = new HashMap<>();
    private final Map<Block, BukkitTask> melting = new HashMap<>();

    public ArenaListener(Plugin plugin, Game game, Messages messages, BedWarsConfig config) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlaceOutside(BlockPlaceEvent event) {
        if (!inRound()) {
            return;
        }
        var block = event.getBlockPlaced();
        if (game.border().filter(border -> border.contains(block.getX(), block.getZ())).isEmpty()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("build.outside"));
        } else if (game.blocksSpawn(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("build.spawn"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeb(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        if (!inRound() || block.getType() != Material.COBWEB || game.guardsBed(block.getLocation(), BED_GUARD_SQUARED)) {
            return;
        }
        var player = event.getPlayer();
        long now = System.currentTimeMillis();
        long last = lastWeb.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < config.webCooldownSeconds() * 1000L) {
            event.setCancelled(true);
            player.sendMessage(messages.get("build.web-cooldown", Formatter.number("seconds", config.webCooldownSeconds())));
            return;
        }
        lastWeb.put(player.getUniqueId(), now);
        var replaced = event.getBlockReplacedState();
        melting.put(block, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            melting.remove(block);
            placed.remove(block);
            replaced.update(true, false);
        }, config.webLifetimeSeconds() * 20L));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlaced(BlockPlaceEvent event) {
        if (!inRound()) {
            return;
        }
        var block = event.getBlockPlaced();
        if (block.getType() == Material.TNT) {
            block.setType(Material.AIR);
            block.getWorld().spawn(block.getLocation().add(0.5, 0.0, 0.5), TNTPrimed.class, tnt -> {
                tnt.setVelocity(new Vector());
                tnt.setSource(event.getPlayer());
            });
            return;
        }
        placed.add(block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!inRound()) {
            return;
        }
        var block = event.getBlock();
        var bed = game.bedAt(block);
        if (bed.isPresent()) {
            game.breakBed(event, bed.get());
            return;
        }
        if (placed.remove(block)) {
            stopMelting(block);
        } else if (Game.isDecoration(block)) {
            event.setDropItems(false);
        } else {
            event.setCancelled(true);
        }
    }

    /** A block players placed drops as the shop's item again, so that it stacks with what they bought. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (!inRound()) {
            return;
        }
        for (var item : event.getItems()) {
            var stack = item.getItemStack();
            game.shop().catalog().plainBlock(stack.getType())
                    .ifPresent(offer -> item.setItemStack(game.shop().item(event.getPlayer(), offer).asQuantity(stack.getAmount())));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (inRound()) {
            keepMap(event.blockList());
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (inRound()) {
            keepMap(event.blockList());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (inRound() && event.getTo().getY() < game.voidHeight()) {
            game.fell(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeamHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var damager = event.getDamager();
        var attacker = damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                ? shooter
                : damager instanceof Player player ? player : null;
        if (attacker != null && !attacker.equals(victim) && game.sameTeam(attacker, victim)) {
            event.setCancelled(true);
            if (damager instanceof Projectile) {
                damager.remove();
            }
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

    /** Only what players placed goes up; a team chest breaks the way it does by hand. */
    private void keepMap(List<Block> blocks) {
        blocks.removeIf(block -> {
            if (game.chests().blownUp(block)) {
                placed.remove(block);
                return true;
            }
            if (!placed.remove(block)) {
                return true;
            }
            stopMelting(block);
            return false;
        });
    }

    private void stopMelting(Block block) {
        var task = melting.remove(block);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean inRound() {
        return game.phase() == GamePhase.INGAME;
    }
}
