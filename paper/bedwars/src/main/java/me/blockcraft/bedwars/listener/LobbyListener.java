package me.blockcraft.bedwars.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.bedwars.game.Game;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * The waiting lobby, before the round and again after it: nobody is hurt or hungry, nothing is built, dropped or
 * picked up, and of the blocks only doors, trapdoors, buttons and levers answer a click. Whoever falls off is put
 * back on the spawn. A player in build mode is left alone.
 */
public final class LobbyListener implements Listener {

    private static final double FALL_HEIGHT = 0.0;

    private final Game game;
    private final BuildMode buildMode;

    public LobbyListener(Game game, BuildMode buildMode) {
        this.game = game;
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        cancelInLobby(event);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        cancelInLobby(event);
    }

    @EventHandler
    public void onExperience(PlayerExpChangeEvent event) {
        if (inLobby()) {
            event.setAmount(0);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!inLobby() || building(event.getPlayer())) {
            return;
        }
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !opens(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (inLobby() && !building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventory(InventoryClickEvent event) {
        if (inLobby() && event.getWhoClicked() instanceof Player player && !building(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (inLobby() && !building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (inLobby() && event.getEntity() instanceof Player player && !building(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (inLobby() && !building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (inLobby() && !building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (inLobby() && !building(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        cancelInLobby(event);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        cancelInLobby(event);
    }

    @EventHandler
    public void onFall(PlayerMoveEvent event) {
        if (inLobby() && event.getTo().getY() < FALL_HEIGHT) {
            game.lobbySpawn().ifPresent(spawn -> {
                event.getPlayer().teleport(spawn);
                spawn.getWorld().playEffect(spawn, Effect.ENDER_SIGNAL, 0);
            });
        }
    }

    private void cancelInLobby(Cancellable event) {
        if (inLobby()) {
            event.setCancelled(true);
        }
    }

    private boolean inLobby() {
        return game.phase() != GamePhase.INGAME;
    }

    private boolean building(Player player) {
        return buildMode.isEnabled(player);
    }

    private static boolean opens(Block block) {
        var type = block.getType();
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.BUTTONS.isTagged(type)
                || type == Material.LEVER;
    }
}
