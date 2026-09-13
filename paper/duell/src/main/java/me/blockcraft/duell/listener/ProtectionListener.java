package me.blockcraft.duell.listener;

import me.blockcraft.duell.game.Duel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Set;

public final class ProtectionListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> BLOCKED_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.DROWNING);

    private final Duel duel;

    public ProtectionListener(Duel duel) {
        this.duel = duel;
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setCancelled(!event.getPlayer().hasPermission("blockcraft.duell.build"));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        event.setCancelled(!event.getPlayer().hasPermission("blockcraft.duell.build"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && BLOCKED_CAUSES.contains(event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && !duel.pvp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (duel.isLobby() || !duel.pvp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }
}
