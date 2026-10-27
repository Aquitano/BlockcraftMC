package me.blockcraft.zombies;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.api.world.MapSaveEvent;
import me.blockcraft.zombies.game.Survivor;
import me.blockcraft.zombies.game.ZombiesGame;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GameListener implements Listener {

    private final Plugin plugin;
    private final ZombiesGame game;
    private final Map<UUID, Integer> knifeReady = new HashMap<>();

    public GameListener(Plugin plugin, ZombiesGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        game.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        game.quit(event.getPlayer());
        knifeReady.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReadNote(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        var player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || building(player)
                || game.running() && !game.fighting(player)) {
            return;
        }
        game.arena().noteAt(block).ifPresent(note -> {
            event.setCancelled(true);
            game.notes().read(player, note);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !game.running() || building(event.getPlayer())) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY || event.hasBlock() && event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        var player = event.getPlayer();
        event.setCancelled(true);
        if (!game.fighting(player)) {
            return;
        }
        if (event.getAction().isRightClick()) {
            var block = event.getClickedBlock();
            if (block != null) {
                var door = game.arena().doorAt(block).filter(room -> !game.arena().unlocked(room.id()));
                if (door.isPresent()) {
                    game.buyDoor(player, door.get());
                    return;
                }
                var station = game.arena().stationAt(block);
                if (station.isPresent()) {
                    game.openStation(player, station.get());
                    return;
                }
            }
            game.weapons().shoot(player, game.tickNumber());
        } else if (event.getAction().isLeftClick()) {
            game.weapons().reload(player, game.tickNumber());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (game.fighting(player) && event instanceof EntityDamageByEntityEvent attack && game.enemies().contains(attack.getDamager())) {
                game.hurt(player, event.getFinalDamage());
            }
            return;
        }
        if (!game.enemies().contains(event.getEntity())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Player player) {
            if (game.enemies().applyingDamage()) {
                return;
            }
            event.setCancelled(true);
            if (game.fighting(player) && player.getInventory().getItemInMainHand().getType() == Material.IRON_SWORD
                    && event.getEntity() instanceof Mob mob && game.tickNumber() >= knifeReady.getOrDefault(player.getUniqueId(), 0)) {
                knifeReady.put(player.getUniqueId(), game.tickNumber() + Math.max(1, plugin.getConfig().getInt("combat.knife-cooldown-ticks", 10)));
                game.enemies().hit(player, mob, game.config().knifeDamage());
            }
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (game.running() && game.weapons().held(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
            game.weapons().reload(event.getPlayer(), game.tickNumber());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (game.running() && !building(event.getPlayer())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            var mystery = game.mysteryBox().stationOf(event.getRightClicked());
            if (mystery.isPresent()) {
                game.openStation(event.getPlayer(), mystery.get());
            } else {
                game.weapons().shoot(event.getPlayer(), game.tickNumber());
            }
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (game.enemies().contains(event.getEntity()) && event.getTarget() != null
                && (!(event.getTarget() instanceof Player player) || !game.fighting(player) || !game.enemies().breached(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnemyDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && game.enemies().contains(mob)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            var spot = game.enemies().dropSpot(mob);
            game.killed(game.enemies().killed(mob), mob, spot);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        game.unexpectedDeath(event.getPlayer());
        Tasks.later(plugin, 1, () -> event.getPlayer().spigot().respawn());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(game.arena().point(game.running() ? "spawn" : "lobby"));
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        if (game.running() && game.survivor(event.getPlayer()) != null) {
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var player = event.getPlayer();
        if (!game.running() || building(player) || !event.hasChangedPosition()) {
            return;
        }
        var survivor = game.survivor(player);
        if (survivor == null || survivor.life() == Survivor.Life.OUT) {
            return;
        }
        if (game.downed(player) || !game.arena().enterable(event.getTo())) {
            var to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (game.running() && game.survivor(event.getPlayer()) != null && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && !game.arena().enterable(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (game.running() && event.getWhoClicked() instanceof Player player && !building(player)) {
            if (!(event.getView().getTopInventory().getHolder(false) instanceof me.blockcraft.api.menu.Menu)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (game.running() && event.getWhoClicked() instanceof Player player && !building(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !building(player)) {
            game.collect(player, event.getItem());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent event) {
        game.refreshSidebar();
        if (game.survivor(event.getPlayer()) != null) {
            game.weapons().render(event.getPlayer());
        }
    }

    @EventHandler
    public void onSpectateRequest(SpectateRequestEvent event) {
        game.follow(event.getPlayer(), event.target());
    }

    @EventHandler
    public void onVanish(VanishChangeEvent event) {
        if (event.isVanished()) {
            game.vanished(event.getPlayer());
            knifeReady.remove(event.getPlayer().getUniqueId());
        }
        game.refreshSidebar();
    }

    @EventHandler
    public void onMapSave(MapSaveEvent event) {
        var current = game.arena().snapshot();
        game.arena().restoreBlocks();
        event.afterSave(() -> current.forEach(state -> state.update(true, false)));
    }

    private static boolean building(Player player) {
        return BlockcraftApi.get().buildMode().isEnabled(player);
    }
}
