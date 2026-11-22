package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Stalks whoever strays from the team, leaps on them and pins them down until a teammate shoots it off. */
final class Pouncer implements Ability {

    private static final double REACH = 1.7;
    private static final int LEAP_TICKS = 20;
    private static final int LANDED_AFTER = 5;

    private enum Stage { CROUCH, LEAP, PIN }

    private static final class Attack {
        final UUID prey;
        final int started;
        Stage stage = Stage.CROUCH;
        int until;
        int since;

        Attack(UUID prey, int started, int until) {
            this.prey = prey;
            this.started = started;
            this.until = until;
        }
    }

    private final ZombiesGame game;
    private final Enemies enemies;
    private final ZombiesConfig.Pouncer config;
    private final Map<Enemies.Enemy, Attack> attacks = new HashMap<>();

    Pouncer(ZombiesGame game, Enemies enemies, ZombiesConfig.Pouncer config) {
        this.game = game;
        this.enemies = enemies;
        this.config = config;
    }

    @Override
    public boolean holds(Enemies.Enemy enemy) {
        return attacks.containsKey(enemy);
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player nearest, int tick) {
        var fighters = game.fighters();
        var prey = loner(enemy.mob.getLocation().toVector(), fighters, player -> player.getLocation().toVector(), config.aloneRadius());
        if (prey.isEmpty()) {
            return false;
        }
        var target = prey.get();
        var mob = enemy.mob;
        if (tick >= enemy.specialAt && mob.getLocation().distanceSquared(target.getLocation()) <= config.leapRange() * config.leapRange()
                && mob.hasLineOfSight(target)) {
            attacks.put(enemy, new Attack(target.getUniqueId(), tick, tick + config.windupTicks()));
            mob.setAware(false);
            mob.getPathfinder().stopPathfinding();
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, SoundCategory.HOSTILE, 1.4f, 0.5f);
            enemies.play(enemy, "crouch");
            return true;
        }
        mob.setTarget(target);
        mob.getPathfinder().moveTo(target, 1.0);
        return true;
    }

    /** The nearest of the fighters who have no other fighter within {@code radius}; in solo play that is the one fighter. */
    static <T> Optional<T> loner(Vector from, Collection<T> fighters, Function<T, Vector> position, double radius) {
        return fighters.stream()
                .filter(fighter -> fighters.stream().noneMatch(other -> other != fighter
                        && position.apply(other).distanceSquared(position.apply(fighter)) <= radius * radius))
                .min(Comparator.comparingDouble(fighter -> position.apply(fighter).distanceSquared(from)));
    }

    @Override
    public void tick(int tick) {
        for (var entry : List.copyOf(attacks.entrySet())) {
            var enemy = entry.getKey();
            var attack = entry.getValue();
            var prey = Bukkit.getPlayer(attack.prey);
            if (!enemy.mob.isValid() || prey == null || !game.fighting(prey)) {
                release(enemy, tick);
                continue;
            }
            switch (attack.stage) {
                case CROUCH -> crouch(enemy, attack, prey, tick);
                case LEAP -> leap(enemy, attack, tick);
                case PIN -> pin(enemy, attack, prey, tick);
            }
        }
    }

    private void crouch(Enemies.Enemy enemy, Attack attack, Player prey, int tick) {
        var mob = enemy.mob;
        CreatureBody.yaw(mob.getLocation(), prey.getLocation()).ifPresent(yaw -> mob.setRotation(yaw, 0));
        mob.getWorld().spawnParticle(Particle.DUST_PLUME, mob.getLocation(), 3, 0.4, 0.05, 0.4, 0.01);
        if (tick < attack.until) {
            return;
        }
        var toward = prey.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
        double distance = toward.length();
        if (distance > 0) {
            toward.normalize().multiply(Math.min(1.4, distance * 0.2));
        }
        mob.setVelocity(toward.setY(0.45));
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 1.2f, 1.4f);
        enemies.play(enemy, "attack");
        attack.stage = Stage.LEAP;
        attack.since = tick;
        attack.until = tick + LEAP_TICKS;
    }

    private void leap(Enemies.Enemy enemy, Attack attack, int tick) {
        var mob = enemy.mob;
        var caught = game.fighters().stream()
                .filter(fighter -> fighter.getLocation().distanceSquared(mob.getLocation()) <= REACH * REACH && !pinned(fighter))
                .findFirst();
        if (caught.isPresent()) {
            var player = caught.get();
            var pin = new Attack(player.getUniqueId(), attack.started, tick + (game.fighters().size() == 1 ? config.soloPinTicks() : config.pinTicks()));
            pin.stage = Stage.PIN;
            pin.since = tick;
            attacks.put(enemy, pin);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.5f, 0.6f);
            game.broadcast("pouncer.caught", Placeholder.unparsed("player", player.getName()));
            return;
        }
        if (tick >= attack.until || mob.isOnGround() && tick - attack.since > LANDED_AFTER) {
            release(enemy, tick);
        }
    }

    private void pin(Enemies.Enemy enemy, Attack attack, Player prey, int tick) {
        if (tick >= attack.until) {
            release(enemy, tick);
            return;
        }
        var mob = enemy.mob;
        var at = prey.getLocation();
        mob.teleport(at.clone().setDirection(at.getDirection().setY(0)));
        prey.setVelocity(new Vector(0, Math.min(0, prey.getVelocity().getY()), 0));
        prey.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 6, false, false));
        game.status(prey, game.messages().get("pouncer.pinned"));
        if ((tick - attack.since) % config.pulseTicks() == 0) {
            enemies.play(enemy, "attack");
            game.hurt(prey, enemies.damage(enemy));
        }
    }

    @Override
    public double shot(Enemies.Enemy enemy, Player shooter, double damage, boolean head) {
        var attack = attacks.get(enemy);
        if (attack != null && attack.stage == Stage.PIN && !attack.prey.equals(shooter.getUniqueId())) {
            var prey = Bukkit.getPlayer(attack.prey);
            if (prey != null) {
                BlockcraftApi.get().notifications().feedback(prey,
                        game.messages().get("pouncer.freed", Placeholder.unparsed("player", shooter.getName())));
            }
            release(enemy, game.tickNumber());
        }
        return damage;
    }

    private void release(Enemies.Enemy enemy, int tick) {
        var attack = attacks.remove(enemy);
        enemy.specialAt = tick + config.cooldownTicks();
        if (!enemy.mob.isValid()) {
            return;
        }
        enemy.mob.setAware(true);
        if (attack != null && attack.stage == Stage.PIN) {
            enemy.mob.setVelocity(enemy.facing().multiply(-0.6).setY(0.3));
        }
    }

    boolean pinned(Player player) {
        return attacks.values().stream().anyMatch(attack -> attack.stage == Stage.PIN && attack.prey.equals(player.getUniqueId()));
    }

    @Override
    public void died(Enemies.Enemy enemy) {
        release(enemy, game.tickNumber());
    }

    @Override
    public void clear() {
        attacks.clear();
    }
}
