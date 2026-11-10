package me.blockcraft.zombies.game;

import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enemies set burning by a gun, and the fighters they hit. Zombies cancels real fire damage, so an enemy's fire is only
 * drawn and hurts through {@link Enemies#hit} in the shooter's name, who earns the gold and the kill; a fighter's is
 * vanilla fire ticks, which wear off and go out in water by themselves, and hurts through {@link ZombiesGame#burn}.
 */
final class Flames {

    private static final int INTERVAL = 10;

    private record Burn(UUID shooter, double damage, int until) {
    }

    private final ZombiesGame game;
    private final Map<Mob, Burn> burning = new HashMap<>();

    Flames(ZombiesGame game) {
        this.game = game;
    }

    void ignite(Player shooter, Mob mob, double damagePerSecond, int ticks, int tick) {
        burning.put(mob, new Burn(shooter.getUniqueId(), damagePerSecond * INTERVAL / 20, tick + ticks));
        mob.setVisualFire(TriState.TRUE);
    }

    void struck(Entity damager, Player player) {
        if (damager instanceof Mob mob && burning.containsKey(mob)) {
            player.setFireTicks(Math.max(player.getFireTicks(), game.config().burnTicks()));
        }
    }

    void tick(int tick) {
        if (tick % INTERVAL != 0) {
            return;
        }
        for (var fighter : game.fighters()) {
            if (fighter.getFireTicks() > 0) {
                game.burn(fighter, game.config().burnDamage() * INTERVAL / 20);
            }
        }
        burning.entrySet().removeIf(entry -> {
            var mob = entry.getKey();
            var burn = entry.getValue();
            if (!mob.isValid() || mob.isDead() || tick >= burn.until()) {
                mob.setVisualFire(TriState.NOT_SET);
                return true;
            }
            var shooter = Bukkit.getPlayer(burn.shooter());
            if (shooter != null) {
                game.enemies().hit(shooter, mob, burn.damage());
            }
            return false;
        });
    }
}
