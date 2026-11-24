package me.blockcraft.zombies.game;

import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Ignores the fighters while the power is on and gnaws through the generator's cable; leave them be and the lights go out. */
final class Scavenger implements Ability {

    private static final int WARNING_TICKS = 200;
    /** The length of the rig's gnaw clip. */
    private static final int GNAW_CLIP_TICKS = 10;
    private final ZombiesGame game;
    private final Enemies enemies;
    private final ZombiesConfig.Scavenger config;
    private int gnawed;
    private int warnedAt = -WARNING_TICKS;

    Scavenger(ZombiesGame game, Enemies enemies, ZombiesConfig.Scavenger config) {
        this.game = game;
        this.enemies = enemies;
        this.config = config;
    }

    /** Read each time, because the arena is the voted map's copy only once the lobby closes. */
    private Optional<Location> generator() {
        return game.arena().generator().map(at -> at.clone().add(0.5, 0, 0.5));
    }

    @Override
    public int pack() {
        return config.pack();
    }

    @Override
    public boolean holds(Enemies.Enemy enemy) {
        return enemy.breached && gnawing(enemy);
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player target, int tick, boolean navigate, boolean useAbility) {
        var generator = generator();
        if (!game.powered() || generator.isEmpty()) {
            return false;
        }
        if (navigate) {
            enemy.mob.setTarget(null);
            enemy.mob.getPathfinder().moveTo(generator.get(), 1.0);
        }
        return true;
    }

    private boolean gnawing(Enemies.Enemy enemy) {
        return game.powered() && generator().filter(at -> at.getWorld().equals(enemy.mob.getWorld())
                && enemy.mob.getLocation().distanceSquared(at) <= config.gnawRadius() * config.gnawRadius()).isPresent();
    }

    @Override
    public void tick(int tick) {
        var generator = generator();
        if (generator.isEmpty() || !game.inWave()) {
            return;
        }
        var gnawers = enemies.of(EnemyKind.SCAVENGER).stream().filter(enemy -> enemy.breached && gnawing(enemy)).toList();
        if (gnawers.isEmpty()) {
            gnawed = Math.max(0, gnawed - 1);
            return;
        }
        var at = generator.get();
        gnawed += gnawers.size();
        if (tick % GNAW_CLIP_TICKS == 0) {
            gnawers.forEach(enemy -> enemies.play(enemy, "gnaw"));
            at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.6, 0), 6, 0.5, 0.3, 0.5, 0.05);
            at.getWorld().playSound(at, Sound.ENTITY_SILVERFISH_AMBIENT, SoundCategory.HOSTILE, 1.2f, 0.6f);
        }
        if (tick - warnedAt >= WARNING_TICKS) {
            warnedAt = tick;
            game.broadcast("scavenger.gnawing");
        }
        if (gnawed >= config.gnawTicks() && game.cutPower()) {
            gnawed = 0;
            game.broadcast("scavenger.cut");
        }
    }

    @Override
    public void clear() {
        gnawed = 0;
    }
}
