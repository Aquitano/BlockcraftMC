package me.blockcraft.zombies.game;

import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Paws the ground, then charges in a straight line. Step aside and it runs into the wall behind you, where it stands
 * stunned and open; from the front its hide takes most of a hit. Outside a window it charges the boards instead.
 */
final class Ram implements Ability {

    private static final double REACH = 1.6;
    private static final double[] SHINS_AND_SHOULDERS = {0.3, 1.2};
    private static final double SMASH_CLOSE = 2.5;
    /** The length of the rig's charge clip, which plays again for as long as the charge lasts. */
    private static final int CHARGE_CLIP_TICKS = 8;

    private enum Stage { PAW, CHARGE, STUNNED }

    private static final class Rush {
        final Optional<UUID> prey;
        final Location aim;
        final List<Block> boards;
        final Set<UUID> struck = new HashSet<>();
        Stage stage = Stage.PAW;
        Vector direction = new Vector();
        int until;

        Rush(Optional<UUID> prey, Location aim, List<Block> boards, int until) {
            this.prey = prey;
            this.aim = aim;
            this.boards = boards;
            this.until = until;
        }
    }

    private final ZombiesGame game;
    private final Enemies enemies;
    private final ZombiesConfig.Ram config;
    private final Map<Enemies.Enemy, Rush> rushes = new HashMap<>();

    Ram(ZombiesGame game, Enemies enemies, ZombiesConfig.Ram config) {
        this.game = game;
        this.enemies = enemies;
        this.config = config;
    }

    @Override
    public boolean holds(Enemies.Enemy enemy) {
        return rushes.containsKey(enemy);
    }

    @Override
    public boolean smash(Enemies.Enemy enemy, List<Block> boards, int tick) {
        if (!rushes.containsKey(enemy)) {
            var center = boards.getFirst().getLocation().add(0.5, 0, 0.5);
            paw(enemy, new Rush(Optional.empty(), center, List.copyOf(boards), tick + config.windupTicks()));
        }
        return true;
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        var mob = enemy.mob;
        double distance = mob.getLocation().distance(target.getLocation());
        if (tick < enemy.specialAt || distance > config.chargeRange() || distance < SMASH_CLOSE || !mob.hasLineOfSight(target)) {
            return false;
        }
        paw(enemy, new Rush(Optional.of(target.getUniqueId()), target.getLocation(), List.of(), tick + config.windupTicks()));
        return true;
    }

    private void paw(Enemies.Enemy enemy, Rush rush) {
        rushes.put(enemy, rush);
        var mob = enemy.mob;
        mob.setAware(false);
        mob.getPathfinder().stopPathfinding();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.6f, 0.8f);
        enemies.play(enemy, "paw");
    }

    @Override
    public void tick(int tick) {
        for (var entry : List.copyOf(rushes.entrySet())) {
            var enemy = entry.getKey();
            var rush = entry.getValue();
            if (!enemy.mob.isValid()) {
                rushes.remove(enemy);
                continue;
            }
            switch (rush.stage) {
                case PAW -> paw(enemy, rush, tick);
                case CHARGE -> charge(enemy, rush, tick);
                case STUNNED -> stunned(enemy, rush, tick);
            }
        }
    }

    private void paw(Enemies.Enemy enemy, Rush rush, int tick) {
        var mob = enemy.mob;
        var aim = rush.prey.map(Bukkit::getPlayer).filter(game::fighting).map(Player::getLocation).orElse(rush.aim);
        CreatureBody.yaw(mob.getLocation(), aim).ifPresent(yaw -> mob.setRotation(yaw, 0));
        if (tick % 5 == 0) {
            floor(mob.getLocation(), 6);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1, 0.6f);
        }
        if (tick < rush.until) {
            return;
        }
        var direction = aim.toVector().subtract(mob.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() == 0) {
            end(enemy, tick);
            return;
        }
        rush.direction = direction.normalize();
        rush.stage = Stage.CHARGE;
        rush.until = tick + config.chargeTicks();
        enemies.play(enemy, "charge");
        if (!rush.boards.isEmpty()) {
            rush.boards.forEach(board -> {
                board.getWorld().spawnParticle(Particle.BLOCK, board.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, board.getBlockData());
                BlockChanges.set(board, Material.AIR);
            });
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 1.4f, 0.7f);
        }
    }

    private void charge(Enemies.Enemy enemy, Rush rush, int tick) {
        var mob = enemy.mob;
        var at = mob.getLocation();
        if (blocked(at, rush.direction, config.chargeSpeed() + 0.4)) {
            rush.stage = Stage.STUNNED;
            rush.until = tick + config.stunTicks();
            mob.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 1.6f, 0.5f);
            mob.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(rush.direction).add(0, 1, 0), 1);
            enemies.play(enemy, "stunned");
            return;
        }
        if (tick >= rush.until) {
            end(enemy, tick);
            return;
        }
        var next = at.clone().add(rush.direction.clone().multiply(config.chargeSpeed()));
        next.setDirection(rush.direction);
        mob.teleport(next);
        if ((rush.until - tick) % CHARGE_CLIP_TICKS == 0) {
            enemies.play(enemy, "charge");
        }
        if (tick % 2 == 0) {
            floor(next, 4);
            mob.getWorld().playSound(next, Sound.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1.2f, 0.8f);
        }
        for (var fighter : game.fighters()) {
            var offset = fighter.getLocation().toVector().subtract(next.toVector());
            if (rush.struck.contains(fighter.getUniqueId()) || Math.abs(offset.getY()) > 2 || offset.setY(0).lengthSquared() > REACH * REACH) {
                continue;
            }
            rush.struck.add(fighter.getUniqueId());
            fighter.damage(enemies.damage(enemy), mob);
            fighter.setVelocity(rush.direction.clone().multiply(config.knockback()).setY(0.5));
        }
    }

    private void stunned(Enemies.Enemy enemy, Rush rush, int tick) {
        var mob = enemy.mob;
        if (tick % 4 == 0) {
            var head = mob.getLocation().add(rush.direction.clone().multiply(0.8)).add(0, mob.getHeight() + 0.3, 0);
            mob.getWorld().spawnParticle(Particle.ENCHANTED_HIT, head, 4, 0.3, 0.1, 0.3, 0);
        }
        if (tick >= rush.until) {
            end(enemy, tick);
        }
    }

    private void end(Enemies.Enemy enemy, int tick) {
        rushes.remove(enemy);
        enemy.specialAt = tick + config.cooldownTicks();
        enemy.mob.setAware(true);
    }

    /** Whether a wall stands within {@code distance} ahead at shin or shoulder height. */
    private static boolean blocked(Location at, Vector direction, double distance) {
        for (double height : SHINS_AND_SHOULDERS) {
            var from = at.clone().add(0, height, 0);
            if (at.getWorld().rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true) != null) {
                return true;
            }
        }
        return false;
    }

    private static void floor(Location at, int count) {
        var ground = at.clone().subtract(0, 0.5, 0).getBlock();
        if (ground.getType().isSolid()) {
            at.getWorld().spawnParticle(Particle.BLOCK, at, count, 0.5, 0.05, 0.5, 0, ground.getBlockData());
        }
    }

    @Override
    public double shot(Enemies.Enemy enemy, Player shooter, double damage, boolean head) {
        var rush = rushes.get(enemy);
        if (rush != null && rush.stage == Stage.STUNNED) {
            return damage * config.stunnedMultiplier();
        }
        var toShooter = shooter.getLocation().toVector().subtract(enemy.mob.getLocation().toVector());
        if (ShieldBearer.blocks(enemy.facing(), toShooter, config.frontAngle())) {
            return damage * config.frontShare();
        }
        return damage;
    }

    @Override
    public void died(Enemies.Enemy enemy) {
        rushes.remove(enemy);
    }

    @Override
    public void clear() {
        rushes.clear();
    }
}
