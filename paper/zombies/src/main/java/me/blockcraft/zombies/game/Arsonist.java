package me.blockcraft.zombies.game;

import com.destroystokyo.paper.entity.ai.GoalType;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Keeps its distance once inside and lobs Molotovs; where one lands the ground burns for a few seconds. */
final class Arsonist implements Ability {

    /** Every tick, vanilla pulls a thrown item down by gravity, slows it by drag, then moves it by what is left. */
    static final double GRAVITY = 0.03;
    static final double DRAG = 0.99;
    private static final Color SOOT = Color.fromRGB(0x3B, 0x2A, 0x20);

    private record Thrown(Snowball bottle, double damage) { }

    private record Fire(Location center, double damage, int until) { }

    private final ZombiesGame game;
    private final ZombiesConfig.Arsonist config;
    private final Map<UUID, Thrown> flying = new HashMap<>();
    private final List<Fire> fires = new ArrayList<>();

    Arsonist(ZombiesGame game, ZombiesConfig.Arsonist config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_ARSONIST_MASK.item().build());
        equipment.setChestplate(Enemies.gear(Items.of(Material.LEATHER_CHESTPLATE).color(SOOT)));
        equipment.setLeggings(Enemies.gear(Items.of(Material.LEATHER_LEGGINGS).color(SOOT)));
        equipment.setItemInMainHand(PackModel.ZOMBIES_MOLOTOV.item().build());
        // A zombie that picks its own target runs at it, which would undo the distance it keeps.
        Bukkit.getMobGoals().removeAllGoals(mob, GoalType.TARGET);
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        var mob = enemy.mob;
        var here = mob.getLocation();
        double distance = here.distance(target.getLocation());
        boolean inside = game.arena().enterable(here);
        boolean sight = mob.hasLineOfSight(target);
        mob.lookAt(target);
        if (distance < config.keepDistance()) {
            retreat(mob, target, distance);
        } else if (distance > config.throwRange() - 1 || !sight || !inside) {
            mob.getPathfinder().moveTo(target, 1.0);
        } else {
            mob.getPathfinder().stopPathfinding();
        }
        if (tick >= enemy.specialAt && inside && sight && distance <= config.throwRange()) {
            enemy.specialAt = tick + config.throwTicks();
            toss(mob, target, game.enemies().damage(enemy));
        }
        return true;
    }

    private void retreat(Mob mob, Player target, double distance) {
        var away = mob.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        var spot = away.lengthSquared() == 0 ? null : mob.getLocation().add(away.normalize().multiply(config.keepDistance() - distance + 2));
        if (spot != null && game.arena().enterable(spot)) {
            mob.getPathfinder().moveTo(spot, 1.2);
        } else {
            mob.getPathfinder().stopPathfinding();
        }
    }

    private void toss(Mob mob, Player target, double damage) {
        var from = mob.getEyeLocation();
        var velocity = launch(from.toVector(), target.getLocation().toVector(), config.flightTicks());
        var bottle = mob.getWorld().spawn(from, Snowball.class, snowball -> {
            snowball.setItem(PackModel.ZOMBIES_MOLOTOV.item().build());
            snowball.setShooter(mob);
            snowball.setPersistent(false);
            snowball.setVelocity(velocity);
        });
        flying.put(bottle.getUniqueId(), new Thrown(bottle, damage));
        mob.swingMainHand();
        mob.getWorld().playSound(from, Sound.ENTITY_WITCH_THROW, 1, 0.8f);
    }

    /** The velocity that carries a thrown item from {@code from} to {@code to} in exactly {@code ticks} ticks. */
    static Vector launch(Vector from, Vector to, int ticks) {
        double carried = DRAG * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
        double terminal = -GRAVITY * DRAG / (1 - DRAG);
        var offset = to.clone().subtract(from);
        return new Vector(offset.getX() / carried, (offset.getY() - terminal * ticks) / carried + terminal, offset.getZ() / carried);
    }

    void landed(Projectile projectile) {
        var thrown = flying.remove(projectile.getUniqueId());
        if (thrown == null) {
            return;
        }
        var at = projectile.getLocation().add(0, 0.2, 0);
        var ground = at.getWorld().rayTraceBlocks(at, new Vector(0, -1, 0), 4, FluidCollisionMode.NEVER, true);
        var center = ground == null ? at : ground.getHitPosition().toLocation(at.getWorld());
        var world = center.getWorld();
        world.spawnParticle(Particle.ITEM, center, 12, 0.2, 0.2, 0.2, 0.1, PackModel.ZOMBIES_MOLOTOV.item().build());
        world.spawnParticle(Particle.FLAME, center, 30, 0.6, 0.3, 0.6, 0.05);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1, 0.9f);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1, 0.8f);
        fires.add(new Fire(center, thrown.damage(), game.tickNumber() + config.fireTicks()));
    }

    @Override
    public void tick(int tick) {
        flying.values().removeIf(thrown -> !thrown.bottle().isValid());
        fires.removeIf(fire -> tick >= fire.until());
        for (var fire : fires) {
            burn(fire, tick);
            if ((fire.until() - tick) % config.pulseTicks() == 0) {
                scorch(fire);
            }
        }
    }

    private void burn(Fire fire, int tick) {
        var world = fire.center().getWorld();
        if (tick % 2 == 0) {
            for (int flame = 0; flame < 6; flame++) {
                world.spawnParticle(Particle.FLAME, spot(fire), 1, 0, 0.05, 0, 0.01);
            }
        }
        if (tick % 4 == 0) {
            world.spawnParticle(Particle.SMOKE, spot(fire), 2, 0.1, 0.2, 0.1, 0.01);
        }
        if (tick % 10 == 0) {
            world.spawnParticle(Particle.LAVA, spot(fire), 1);
        }
        if (tick % 20 == 0) {
            world.playSound(fire.center(), Sound.BLOCK_FIRE_AMBIENT, 1, 1);
        }
    }

    private Location spot(Fire fire) {
        var random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double radius = config.fireRadius() * Math.sqrt(random.nextDouble());
        return fire.center().clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
    }

    /** The ground's fire hurts through the game directly, since the arsonist that threw it may be dead by now. */
    private void scorch(Fire fire) {
        double radius = config.fireRadius();
        for (var fighter : game.fighters()) {
            var feet = fighter.getLocation();
            double dx = feet.getX() - fire.center().getX();
            double dz = feet.getZ() - fire.center().getZ();
            if (dx * dx + dz * dz <= radius * radius && Math.abs(feet.getY() - fire.center().getY()) <= 1.5) {
                game.hurt(fighter, fire.damage());
                fighter.getWorld().spawnParticle(Particle.FLAME, feet.add(0, 0.5, 0), 6, 0.2, 0.4, 0.2, 0.02);
            }
        }
    }

    @Override
    public void clear() {
        flying.values().forEach(thrown -> thrown.bottle().remove());
        flying.clear();
        fires.clear();
    }
}
