package me.blockcraft.sternenfront.game;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.combat.Guard;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The bolts in flight. Each moves {@code speed} blocks a tick along a straight line, stops at the first wall or
 * enemy on the way and shows as a bright model in its faction's colour with a trail of the same colour, which a
 * client without the pack still sees. Teammates, deploying players and spectators are not in the way; a marked
 * target of the map is, and hears of it through a {@link TargetShotEvent}.
 */
public final class Bolts {

    private static final double HIT_SIZE = 0.1;
    private static final double TRAIL_STEP = 0.5;
    private static final float TRAIL_SIZE = 0.7f;
    private static final double VISUAL_LEAD = 0.8;
    private static final double HEAD_BELOW_EYES = 0.25;
    private static final int SPARKS = 6;
    private static final float IMPACT_VOLUME = 0.6f;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private record Shot(Faction faction, Blaster blaster, double damage, double headshot, double speed) { }

    private static final class Bolt {
        private final LivingEntity shooter;
        private final Shot shot;
        private final Vector direction;
        private final ItemDisplay display;
        private Location position;
        private double left;

        private Bolt(LivingEntity shooter, Shot shot, Location position, Vector direction, double range, ItemDisplay display) {
            this.shooter = shooter;
            this.shot = shot;
            this.position = position;
            this.direction = direction;
            this.left = range;
            this.display = display;
        }
    }

    private final SternenfrontGame game;
    private final List<Bolt> flying = new ArrayList<>();

    public Bolts(SternenfrontGame game) {
        this.game = game;
    }

    /** One bolt from the shooter's eyes, off their aim by up to the blaster's spread. */
    public void fire(Player shooter, Faction faction, Blaster blaster, Blaster.Stats stats) {
        var eye = shooter.getEyeLocation();
        var direction = eye.getDirection();
        if (stats.spread() > 0) {
            var random = ThreadLocalRandom.current();
            direction.add(new Vector(random.nextDouble(-stats.spread(), stats.spread()),
                    random.nextDouble(-stats.spread(), stats.spread()),
                    random.nextDouble(-stats.spread(), stats.spread()))).normalize();
        }
        if (blaster == Blaster.LONG_RIFLE && game.combat().config().precisionHitscan()) {
            launch(shooter, new Shot(faction, blaster, stats.damage(), stats.headshot(), stats.range()),
                    shooter.getEyeLocation(), direction, stats.range(), true);
        } else {
            fire(shooter, faction, blaster, stats, direction);
        }
    }

    public void fire(LivingEntity shooter, Faction faction, Blaster blaster, Blaster.Stats stats, Vector direction) {
        launch(shooter, new Shot(faction, blaster, stats.damage(), stats.headshot(), stats.speed()), shooter.getEyeLocation(), direction, stats.range());
    }

    private void launch(LivingEntity shooter, Shot shot, Location from, Vector direction, double range) {
        launch(shooter, shot, from, direction, range, false);
    }

    private void launch(LivingEntity shooter, Shot shot, Location from, Vector direction, double range, boolean instant) {
        var shown = from.clone().add(direction.clone().multiply(VISUAL_LEAD)).setDirection(direction);
        var display = from.getWorld().spawn(shown, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setItemStack(model(shot.faction()).item().build());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setBrightness(LIT);
            spawned.setTeleportDuration(1);
        });
        var bolt = new Bolt(shooter, shot, from.clone(), direction.clone(), range, display);
        if (instant) {
            step(bolt);
            display.remove();
        } else {
            flying.add(bolt);
        }
    }

    public void tick() {
        var bolts = List.copyOf(flying);
        flying.clear();
        for (var bolt : bolts) {
            if (step(bolt)) {
                flying.add(bolt);
            } else {
                bolt.display.remove();
            }
        }
    }

    /** Moves the bolt one tick; false once it hit something or ran out of range. */
    private boolean step(Bolt bolt) {
        var world = bolt.position.getWorld();
        double step = Math.min(bolt.shot.speed(), bolt.left);
        var start = bolt.position.toVector();
        var wall = world.rayTraceBlocks(bolt.position, bolt.direction, step, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? step : wall.getHitPosition().distance(start);
        var hit = reach <= 0 ? null : world.rayTraceEntities(bolt.position, bolt.direction, reach, HIT_SIZE,
                entity -> entity instanceof LivingEntity target && game.hittable(bolt.shot.faction(), target) || isTarget(entity));
        double travelled = hit == null ? reach : hit.getHitPosition().distance(start);
        trail(bolt, travelled);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity victim) {
            var at = hit.getHitPosition().toLocation(world);
            sparks(at, bolt.shot.faction());
            strike(bolt, victim, at);
            return false;
        }
        if (hit != null && hit.getHitEntity() instanceof Interaction target) {
            var at = hit.getHitPosition().toLocation(world);
            sparks(at, bolt.shot.faction());
            SoundCue.BOLT_IMPACT.play(at, IMPACT_VOLUME);
            if (bolt.shooter instanceof Player player && player.isOnline()) {
                Bukkit.getPluginManager().callEvent(new TargetShotEvent(target, player));
            }
            return false;
        }
        if (wall != null || bolt.left - step <= 0) {
            var at = bolt.position.clone().add(bolt.direction.clone().multiply(travelled));
            sparks(at, bolt.shot.faction());
            if (wall != null) {
                SoundCue.BOLT_IMPACT.play(at, IMPACT_VOLUME);
            }
            return false;
        }
        bolt.position = bolt.position.add(bolt.direction.clone().multiply(step));
        bolt.left -= step;
        bolt.display.teleport(bolt.position.clone().setDirection(bolt.direction));
        return true;
    }

    private void strike(Bolt bolt, LivingEntity victim, Location at) {
        var guard = victim instanceof Player player ? game.combat().block(player, bolt.direction, bolt.shot.damage())
                : Guard.Result.NONE;
        if (guard == Guard.Result.BLOCK) {
            return;
        }
        if (victim instanceof Player player && guard == Guard.Result.PARRY) {
            var back = bolt.shooter.isValid() && bolt.shooter.getWorld().equals(at.getWorld())
                    ? bolt.shooter.getEyeLocation().toVector().subtract(at.toVector()).normalize()
                    : bolt.direction.clone().multiply(-1);
            var faction = game.factionOf(victim).orElse(bolt.shot.faction().enemy());
            var shot = new Shot(faction, bolt.shot.blaster(), bolt.shot.damage(), bolt.shot.headshot(), bolt.shot.speed());
            launch(victim, shot, at.clone().add(back.clone().multiply(VISUAL_LEAD)), back, game.config().stats(shot.blaster()).range());
            game.deflected(player, at);
            return;
        }
        boolean head = at.getY() >= victim.getEyeLocation().getY() - HEAD_BELOW_EYES;
        game.boltHit(bolt.shooter, bolt.shot.blaster(), victim, bolt.shot.damage() * (head ? bolt.shot.headshot() : 1), head);
    }

    private static void trail(Bolt bolt, double length) {
        var dust = new Particle.DustOptions(bolt.shot.faction().bolt(), TRAIL_SIZE);
        for (double along = 0; along < length; along += TRAIL_STEP) {
            var point = bolt.position.clone().add(bolt.direction.clone().multiply(along));
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
        }
    }

    private static void sparks(Location at, Faction faction) {
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, SPARKS, 0.1, 0.1, 0.1, 0.05);
        at.getWorld().spawnParticle(Particle.DUST, at, SPARKS, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(faction.bolt(), 1.0f));
    }

    private static boolean isTarget(Entity entity) {
        return entity instanceof Interaction target && target.getPersistentDataContainer().has(TargetShotEvent.MARK);
    }

    private static PackModel model(Faction faction) {
        return faction == Faction.KOLONIEN ? PackModel.STERNENFRONT_BOLT_KOLONIEN : PackModel.STERNENFRONT_BOLT_HEGEMONIE;
    }

    public void clear() {
        flying.forEach(bolt -> bolt.display.remove());
        flying.clear();
    }
}
