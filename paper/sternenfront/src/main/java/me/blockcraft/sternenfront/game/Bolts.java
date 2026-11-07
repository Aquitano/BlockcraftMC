package me.blockcraft.sternenfront.game;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.combat.Guard;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * The bolts in flight. Each moves {@code speed} blocks a tick along a straight line, stops at the first wall or
 * enemy on the way and shows as a bright model in its faction's colour with a trail of the same colour. Teammates,
 * deploying players and spectators are not in the way; a marked target of the map is, and hears of it through a
 * {@link TargetShotEvent}. A player's bolt meets the fighters where that player saw them, as {@link LagCompensation}
 * rewinds.
 */
public final class Bolts {

    private static final double HIT_SIZE = 0.1;
    private static final double VISUAL_LEAD = 1.6;
    private static final double PARRY_CLEARANCE = 0.8;
    private static final double TRAIL_CLEAR_OF_EYES = 1.4;
    private static final float MODEL_LENGTH = 1.22f;
    private static final double HEAD_BELOW_EYES = 0.25;
    private static final float IMPACT_VOLUME = 0.6f;

    /** {@code rewind} is how many ticks behind the server the shooter saw the other fighters. */
    private record Shot(Faction faction, Blaster blaster, double damage, double headshot, double speed, double rewind) { }

    private static final class Bolt {
        private final LivingEntity shooter;
        private final Shot shot;
        private final Vector direction;
        private final ItemDisplay display;
        private Location position;
        private final double range;
        private double left;
        private double nextDot = TRAIL_CLEAR_OF_EYES;
        private int dots;
        private double seen;

        private Bolt(LivingEntity shooter, Shot shot, Location position, Vector direction, double range, ItemDisplay display,
                     double seen) {
            this.shooter = shooter;
            this.shot = shot;
            this.position = position;
            this.direction = direction;
            this.range = range;
            this.left = range;
            this.display = display;
            this.seen = seen;
        }
    }

    private final SternenfrontGame game;
    private final WeaponHandling handling;
    private final LagCompensation lag;
    private final List<Bolt> flying = new ArrayList<>();

    public Bolts(SternenfrontGame game) {
        this.game = game;
        handling = WeaponHandling.from(game.plugin().getConfig().getConfigurationSection("combat.handling"));
        lag = new LagCompensation(LagCompensation.Settings.from(game.plugin().getConfig().getConfigurationSection("combat.lag-compensation")));
    }

    /** One bolt from the shooter's eyes, off their aim by up to the blaster's spread. */
    public void fire(Player shooter, Faction faction, Blaster blaster, Blaster.Stats stats) {
        var eye = shooter.getEyeLocation();
        var direction = eye.getDirection();
        double spread = handling.spread(stats.spread(), shooter, game.fighter(shooter).heat().level());
        if (spread > 0) {
            var random = ThreadLocalRandom.current();
            direction.add(new Vector(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread),
                    random.nextDouble(-spread, spread))).normalize();
        }
        if (blaster.precision() && game.combat().config().precisionHitscan()) {
            launch(shooter, new Shot(faction, blaster, stats.damage(), stats.headshot(), stats.range(), lag.rewind(shooter)),
                    shooter.getEyeLocation(), direction, stats.range(), true);
        } else {
            fire(shooter, faction, blaster, stats, direction);
        }
    }

    public void fire(LivingEntity shooter, Faction faction, Blaster blaster, Blaster.Stats stats, Vector direction) {
        launch(shooter, new Shot(faction, blaster, stats.damage(), stats.headshot(), stats.speed(), lag.rewind(shooter)),
                shooter.getEyeLocation(), direction, stats.range());
    }

    /** One bolt from a muzzle other than the shooter's eyes, as a vehicle's gun has. */
    public void fire(LivingEntity shooter, Faction faction, Blaster blaster, Blaster.Stats stats, Location muzzle, Vector direction) {
        launch(shooter, new Shot(faction, blaster, stats.damage(), stats.headshot(), stats.speed(), lag.rewind(shooter)), muzzle,
                direction, stats.range());
    }

    private void launch(LivingEntity shooter, Shot shot, Location from, Vector direction, double range) {
        launch(shooter, shot, from, direction, range, false);
    }

    /**
     * A shot comes between ticks or after this tick's bolts moved, when the fighters already stand about where the coming
     * tick records them, so that tick is the present its rewind counts back from.
     */
    private void launch(LivingEntity shooter, Shot shot, Location from, Vector direction, double range, boolean instant) {
        double seen = game.tick() + 1 - shot.rewind();
        if (instant) {
            step(new Bolt(shooter, shot, from.clone(), direction.clone(), range, null, seen));
            return;
        }
        var look = game.effects().config().bolt();
        var shown = from.clone().add(direction.clone().multiply(VISUAL_LEAD)).setDirection(direction);
        var display = Displays.item(shown, model(shot.faction()).item().build(), spawned -> {
            spawned.setBrightness(Displays.LIT);
            spawned.setTeleportDuration(1);
            spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(look.width(), look.width(), look.length()), new Quaternionf().rotationY((float) Math.PI)));
        });
        flying.add(new Bolt(shooter, shot, from.clone(), direction.clone(), range, display, seen));
    }

    public void tick() {
        lag.record(game.tick(), game.targets());
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
        var mark = reach <= 0 ? null : world.rayTraceEntities(bolt.position, bolt.direction, reach, HIT_SIZE, Bolts::isTarget);
        var fighter = reach <= 0 ? Optional.<LagCompensation.Hit>empty() : fighterHit(bolt, start, reach)
                .filter(hit -> mark == null || hit.at().distanceSquared(start) < mark.getHitPosition().distanceSquared(start));
        double travelled = fighter.map(hit -> hit.at().distance(start))
                .orElse(mark == null ? reach : mark.getHitPosition().distance(start));
        trail(bolt, travelled);
        if (fighter.isPresent()) {
            var at = fighter.get().at().toLocation(world);
            game.effects().boltHit(at, bolt.shot.faction(), fighter.get().victim());
            strike(bolt, fighter.get(), at, travelled);
            return false;
        }
        if (mark != null && mark.getHitEntity() instanceof Interaction target) {
            var at = mark.getHitPosition().toLocation(world);
            game.effects().boltOnTarget(at, bolt.shot.faction());
            SoundCue.BOLT_IMPACT.play(at, IMPACT_VOLUME);
            if (bolt.shooter instanceof Player player && player.isOnline()) {
                Bukkit.getPluginManager().callEvent(new TargetShotEvent(target, player));
            }
            return false;
        }
        if (wall != null || bolt.left - step <= 0) {
            var at = bolt.position.clone().add(bolt.direction.clone().multiply(travelled));
            if (wall != null) {
                boolean shield = game.gadgets().impact(wall.getHitBlock(), at, bolt.direction);
                var face = wall.getHitBlockFace();
                var normal = face == null ? bolt.direction.clone().multiply(-1) : face.getDirection();
                game.effects().boltOnWall(at, normal, wall.getHitBlock(), shield, bolt.shot.faction());
                SoundCue.BOLT_IMPACT.play(at, IMPACT_VOLUME);
            } else {
                game.effects().boltFizzle(at);
            }
            return false;
        }
        bolt.position = bolt.position.add(bolt.direction.clone().multiply(step));
        bolt.left -= step;
        bolt.seen++;
        bolt.display.teleport(bolt.position.clone().setDirection(bolt.direction));
        return true;
    }

    /** Players' shots meet the fighters where the shooter saw them; terrain and map targets stand still and stay live. */
    private Optional<LagCompensation.Hit> fighterHit(Bolt bolt, Vector start, double reach) {
        Predicate<LivingEntity> hittable = target -> game.hittable(bolt.shot.faction(), target);
        if (bolt.shot.rewind() > 0) {
            return lag.trace(start, bolt.direction, reach, HIT_SIZE, bolt.seen, hittable);
        }
        var hit = bolt.position.getWorld().rayTraceEntities(bolt.position, bolt.direction, reach, HIT_SIZE,
                entity -> entity instanceof LivingEntity target && hittable.test(target));
        return hit != null && hit.getHitEntity() instanceof LivingEntity victim
                ? Optional.of(new LagCompensation.Hit(victim, hit.getHitPosition(), victim.getEyeLocation().getY()))
                : Optional.empty();
    }

    private void strike(Bolt bolt, LagCompensation.Hit hit, Location at, double travelled) {
        var victim = hit.victim();
        double damage = handling.damage(bolt.shot.damage(), bolt.range - bolt.left + travelled, bolt.range);
        var guard = victim instanceof Player player ? game.combat().block(player, bolt.direction, damage)
                : Guard.Result.NONE;
        if (guard == Guard.Result.BLOCK) {
            return;
        }
        if (victim instanceof Player player && guard == Guard.Result.PARRY) {
            var back = bolt.shooter.isValid() && bolt.shooter.getWorld().equals(at.getWorld())
                    ? bolt.shooter.getEyeLocation().toVector().subtract(at.toVector()).normalize()
                    : bolt.direction.clone().multiply(-1);
            var faction = game.factionOf(victim).orElse(bolt.shot.faction().enemy());
            var shot = new Shot(faction, bolt.shot.blaster(), bolt.shot.damage(), bolt.shot.headshot(), bolt.shot.speed(), 0);
            launch(victim, shot, at.clone().add(back.clone().multiply(PARRY_CLEARANCE)), back, game.config().stats(shot.blaster()).range());
            game.deflected(player, at);
            return;
        }
        boolean head = game.speeders().faction(victim).isEmpty() && at.getY() >= hit.eyeY() - HEAD_BELOW_EYES;
        SoundCue.BOLT_IMPACT.play(at, IMPACT_VOLUME);
        game.boltHit(bolt.shooter, bolt.shot.blaster(), victim, damage * (head ? bolt.shot.headshot() : 1),
                head && bolt.shot.headshot() > 1);
    }

    /**
     * Dots along what the bolt covered this tick, clear of the shooter's eyes where it starts. Only every few dots is
     * forced, so a far player gets a sparse line and a near one a dense line without the near rate going to everyone.
     */
    private void trail(Bolt bolt, double length) {
        var look = game.effects().config().bolt();
        boolean hitscan = bolt.display == null;
        if (hitscan && length > TRAIL_CLEAR_OF_EYES) {
            var end = bolt.position.clone().add(bolt.direction.clone().multiply(length));
            game.effects().tracer(bolt.shooter, bolt.direction, model(bolt.shot.faction()).item().build(), MODEL_LENGTH, end);
        }
        double step = hitscan ? look.trailStep() * 2 : look.trailStep();
        var dust = new Particle.DustOptions(bolt.shot.faction().bolt(), look.trailSize());
        double along = bolt.nextDot;
        for (; along < length; along += step) {
            var point = bolt.position.clone().add(bolt.direction.clone().multiply(along));
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust, bolt.dots++ % look.farEvery() == 0);
        }
        bolt.nextDot = along - length;
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
        lag.clear();
    }
}
