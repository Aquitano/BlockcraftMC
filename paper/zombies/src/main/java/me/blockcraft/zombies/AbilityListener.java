package me.blockcraft.zombies;

import me.blockcraft.zombies.game.ZombiesGame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/** Hands the world's events to the enemies' abilities: a landing Molotov, an attack that reached a fighter. */
public final class AbilityListener implements Listener {

    private final ZombiesGame game;

    public AbilityListener(ZombiesGame game) {
        this.game = game;
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        game.enemies().abilities().landed(event.getEntity());
    }

    /** Before {@link GameListener} cancels the hit and routes its damage through the game. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onStrike(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && game.fighting(player)) {
            game.enemies().abilities().struck(event.getDamager(), player);
        }
    }
}
