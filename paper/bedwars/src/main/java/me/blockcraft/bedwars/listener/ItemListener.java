package me.blockcraft.bedwars.listener;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.bedwars.game.Game;
import me.blockcraft.bedwars.game.GameItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * The jump and run's way back to its start, and the arrow trail selected in the perks menu.
 */
public final class ItemListener implements Listener {

    private final Plugin plugin;
    private final Game game;

    public ItemListener(Plugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onHotbar(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        HotbarItem.of(event.getItem(), GameItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case JUMP_RESET -> game.jumpAndRun().ifPresent(run -> run.toStart(event.getPlayer()));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        game.perks().arrowTrail(shooter).ifPresent(trail -> Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arrow.isValid() || arrow.isInBlock()) {
                task.cancel();
                return;
            }
            trail.spawn(arrow.getLocation());
        }, 0L, 1L));
    }
}
