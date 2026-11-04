package me.blockcraft.replay.playback;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Everyone on screen: a mannequin for each participant and a frozen copy of each recorded mob, projectile and primed
 * TNT, placed where the recording says and made to swing, flinch, die and explode when it did, and the displays and
 * dropped items around them.
 */
final class PlaybackCast {

    private static final NamespacedKey FIGURE = new NamespacedKey("blockcraft", "replay_figure");
    private static final List<EquipmentSlot> EQUIPMENT = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final double TELEPORT_DISTANCE_SQUARED = 8 * 8;
    /** Enough velocity to keep a resting projectile pointing the recorded way, too little to see it move. */
    private static final double HEADING = 1.0E-3;
    private static final float EXPLOSION_VOLUME = 4;
    private static final float EXPLOSION_PITCH = 0.7F;
    private static final double SHOT_RANGE = 48;
    private static final double TRACER_SPACING = 1.5;
    private static final float SHOT_VOLUME = 0.35F;
    private static final float SHOT_PITCH = 1.7F;

    private final Player viewer;
    private final PlaybackTimeline timeline;
    private final Map<UUID, World> worlds;
    private final Map<UUID, ReplayHeader.Participant> participants = new HashMap<>();
    private final Map<UUID, Figure> figures = new HashMap<>();
    private final Map<UUID, Fixture> fixtures = new HashMap<>();
    private final Set<UUID> unplayable = new HashSet<>();

    private static final class Figure {
        private final Entity entity;
        private ReplayEvent.Body<?> shown;
        private Vector velocity;

        Figure(Entity entity) {
            this.entity = entity;
        }
    }

    private static final class Fixture {
        private final Entity entity;
        private FixtureTrack.Shown shown;

        Fixture(Entity entity, FixtureTrack.Shown shown) {
            this.entity = entity;
            this.shown = shown;
        }
    }

    PlaybackCast(Player viewer, PlaybackTimeline timeline, Map<UUID, World> worlds) {
        this.viewer = viewer;
        this.timeline = timeline;
        this.worlds = worlds;
        timeline.header().participants().forEach(participant -> participants.put(participant.id(), participant));
    }

    /** Whether the playback put this entity there; anything else spawning in a replay world is refused. */
    static boolean figure(Entity entity) {
        return entity.getPersistentDataContainer().has(FIGURE);
    }

    /**
     * Places everyone at the moment shown. {@code ahead} is the props one playback tick later: projectiles fly on their
     * own velocity between the server's position updates, so they get the recorded motion as theirs.
     */
    void show(List<ReplayEvent.Actor> actors, List<ReplayEvent.Prop> props, List<ReplayEvent.Prop> ahead) {
        var present = new HashSet<UUID>();
        for (var actor : actors) {
            var participant = participants.get(actor.id());
            if (participant != null) {
                place(actor, location -> Optional.of(mannequin(location, participant))).ifPresent(figure -> {
                    present.add(actor.id());
                    dress(figure, actor);
                });
            }
        }
        var next = new HashMap<UUID, ReplayEvent.Prop>();
        ahead.forEach(prop -> next.put(prop.id(), prop));
        for (var prop : props) {
            place(prop, location -> prop(prop.id(), location)).ifPresent(figure -> {
                present.add(prop.id());
                figure.shown = prop;
                if (drifts(figure.entity)) {
                    var velocity = velocity(figure.entity, prop, next.get(prop.id()));
                    if (!velocity.equals(figure.velocity)) {
                        figure.entity.setVelocity(velocity);
                        figure.velocity = velocity;
                    }
                }
            });
        }
        figures.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().entity.remove();
            return true;
        });
    }

    private Optional<Figure> place(ReplayEvent.Body<?> body, Function<Location, Optional<Entity>> spawn) {
        var world = worlds.get(body.world());
        if (world == null) {
            return Optional.empty();
        }
        var location = new Location(world, body.x(), body.y(), body.z(), body.yaw(), body.pitch());
        var figure = figures.get(body.id());
        if (figure != null && (!figure.entity.isValid() || !figure.entity.getWorld().equals(world))) {
            figure.entity.remove();
            figures.remove(body.id());
            figure = null;
        }
        if (figure == null) {
            var spawned = spawn.apply(location);
            if (spawned.isEmpty()) {
                return Optional.empty();
            }
            figure = new Figure(spawned.get());
            figures.put(body.id(), figure);
        } else if (moved(figure.shown, body) || drifts(figure.entity)) {
            figure.entity.teleport(location);
        }
        return Optional.of(figure);
    }

    /** Projectiles and TNT move on their own velocity between pulses, so they are put back on every one. */
    private static boolean drifts(Entity entity) {
        return entity instanceof Projectile || entity instanceof TNTPrimed;
    }

    private static boolean moved(ReplayEvent.Body<?> shown, ReplayEvent.Body<?> body) {
        return shown == null || shown.x() != body.x() || shown.y() != body.y() || shown.z() != body.z()
                || shown.yaw() != body.yaw() || shown.pitch() != body.pitch();
    }

    private static Mannequin mannequin(Location location, ReplayHeader.Participant participant) {
        return location.getWorld().spawn(location, Mannequin.class, mannequin -> {
            var profile = Bukkit.createProfileExact(UUID.randomUUID(), participant.name());
            if (participant.texture() != null && !participant.texture().isBlank()) {
                profile.setProperty(new ProfileProperty("textures", participant.texture(), participant.signature()));
            }
            mannequin.getPersistentDataContainer().set(FIGURE, PersistentDataType.BOOLEAN, true);
            mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
            mannequin.setDescription(null);
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setAI(false);
            mannequin.setSilent(true);
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setCollidable(false);
            mannequin.customName(Component.text(participant.name()));
            mannequin.setCustomNameVisible(true);
        });
    }

    private static void dress(Figure figure, ReplayEvent.Actor actor) {
        var previous = figure.shown instanceof ReplayEvent.Actor shown ? shown : null;
        figure.shown = actor;
        if (!(figure.entity instanceof Mannequin mannequin)) {
            return;
        }
        if (previous == null || !previous.pose().equals(actor.pose())) {
            var pose = Pose.valueOf(actor.pose());
            mannequin.setPose(Mannequin.validPoses().contains(pose) ? pose : Pose.STANDING, true);
        }
        var equipment = mannequin.getEquipment();
        for (int i = 0; i < EQUIPMENT.size(); i++) {
            var bytes = actor.equipment().get(i);
            if (previous == null || !Arrays.equals(previous.equipment().get(i), bytes)) {
                equipment.setItem(EQUIPMENT.get(i), bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes));
            }
        }
    }

    /**
     * A copy of the recorded entity that keeps still until it is moved: no AI, no gravity, no owner, no fuse. Riders
     * in its data are dropped, since they would never join the world.
     */
    private Optional<Entity> prop(UUID id, Location location) {
        return timeline.appearance(id).flatMap(appearance -> spawn(id, appearance, location));
    }

    private Optional<Entity> spawn(UUID id, String appearance, Location location) {
        if (unplayable.contains(id)) {
            return Optional.empty();
        }
        try {
            var entity = Bukkit.getEntityFactory().createEntitySnapshot(appearance).createEntity(location.getWorld());
            entity.eject();
            freeze(entity);
            if (entity.spawnAt(location)) {
                return Optional.of(entity);
            }
        } catch (IllegalArgumentException ignored) {
        }
        unplayable.add(id);
        return Optional.empty();
    }

    private static void freeze(Entity entity) {
        entity.getPersistentDataContainer().set(FIGURE, PersistentDataType.BOOLEAN, true);
        entity.setGravity(false);
        entity.setVelocity(new Vector());
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setPersistent(false);
        switch (entity) {
            case Mob mob -> {
                mob.setDespawnInPeacefulOverride(TriState.FALSE);
                mob.setAI(false);
                mob.setRemoveWhenFarAway(false);
                mob.setCollidable(false);
            }
            case Fireball fireball -> {
                fireball.setShooter(null);
                fireball.setAcceleration(new Vector());
            }
            case Projectile projectile -> projectile.setShooter(null);
            case TNTPrimed tnt -> tnt.setFuseTicks(Integer.MAX_VALUE);
            case Item item -> {
                item.setCanPlayerPickup(false);
                item.setCanMobPickup(false);
                item.setUnlimitedLifetime(true);
            }
            default -> { }
        }
    }

    /**
     * Places the displays and dropped items of the moment shown. A display eases into a new transformation over the
     * recorded ticks divided by {@code speed}; a speed of 0, after a jump, sets it at once.
     */
    void place(List<FixtureTrack.Shown> shown, double speed) {
        var present = new HashSet<UUID>();
        for (var fixture : shown) {
            var world = worlds.get(fixture.world());
            if (world == null) {
                continue;
            }
            var location = new Location(world, fixture.x(), fixture.y(), fixture.z(), fixture.yaw(), fixture.pitch());
            var placed = fixtures.get(fixture.id());
            if (placed != null && (!placed.entity.isValid() || !placed.entity.getWorld().equals(world)
                    || !placed.shown.entity().equals(fixture.entity()))) {
                placed.entity.remove();
                fixtures.remove(fixture.id());
                placed = null;
            }
            if (placed == null) {
                var spawned = spawn(fixture.id(), fixture.entity(), location);
                if (spawned.isEmpty()) {
                    continue;
                }
                transform(spawned.get(), fixture, 0);
                fixtures.put(fixture.id(), new Fixture(spawned.get(), fixture));
            } else {
                if (fixture.x() != placed.shown.x() || fixture.y() != placed.shown.y() || fixture.z() != placed.shown.z()
                        || fixture.yaw() != placed.shown.yaw() || fixture.pitch() != placed.shown.pitch()) {
                    placed.entity.teleport(location);
                }
                if (!Arrays.equals(fixture.transformation(), placed.shown.transformation())) {
                    transform(placed.entity, fixture, speed > 0 ? (int) Math.round(fixture.interpolation() / speed) : 0);
                }
                placed.shown = fixture;
            }
            present.add(fixture.id());
        }
        fixtures.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().entity.remove();
            return true;
        });
    }

    private static void transform(Entity entity, FixtureTrack.Shown fixture, int ticks) {
        var values = fixture.transformation();
        if (!(entity instanceof Display display) || values.length != ReplayEvent.Fixture.TRANSFORMATION_SIZE) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(values[0], values[1], values[2]),
                new Quaternionf(values[3], values[4], values[5], values[6]), new Vector3f(values[7], values[8], values[9]),
                new Quaternionf(values[10], values[11], values[12], values[13])));
    }

    /**
     * The recorded motion over the next playback tick. A resting projectile still gets a trace of velocity along its
     * heading, because a projectile turns to face where it moves every tick.
     */
    private static Vector velocity(Entity entity, ReplayEvent.Prop prop, ReplayEvent.Prop next) {
        if (next != null && next.world().equals(prop.world())) {
            var motion = new Vector(next.x() - prop.x(), next.y() - prop.y(), next.z() - prop.z());
            if (motion.lengthSquared() > HEADING * HEADING && motion.lengthSquared() < TELEPORT_DISTANCE_SQUARED) {
                return motion;
            }
        }
        if (!(entity instanceof Projectile)) {
            return new Vector();
        }
        double yaw = Math.toRadians(prop.yaw());
        double pitch = Math.toRadians(prop.pitch());
        return new Vector(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).multiply(HEADING);
    }

    /** Plays what happened between the previous moment shown and this one; dying and exploding figures leave the cast. */
    void perform(List<ReplayEvent.Animation> animations) {
        for (var animation : animations) {
            var figure = figures.get(animation.subject());
            if (figure == null) {
                continue;
            }
            var entity = figure.entity;
            switch (animation.motion()) {
                case SWING_MAIN_HAND -> {
                    if (entity instanceof LivingEntity living) {
                        living.swingMainHand();
                    }
                }
                case SWING_OFF_HAND -> {
                    if (entity instanceof LivingEntity living) {
                        living.swingOffHand();
                    }
                }
                case HURT -> {
                    if (entity instanceof LivingEntity living) {
                        living.playHurtAnimation(0);
                        sound(living, living.getHurtSound());
                    }
                }
                case DEATH -> {
                    figures.remove(animation.subject());
                    if (entity instanceof LivingEntity living) {
                        sound(living, living.getDeathSound());
                        living.setHealth(0);
                    } else {
                        entity.remove();
                    }
                }
                case SHOT -> {
                    if (entity instanceof LivingEntity living) {
                        shot(living);
                    }
                }
                case EXPLOSION -> {
                    figures.remove(animation.subject());
                    var location = entity.getLocation();
                    entity.getWorld().spawnParticle(entity instanceof Fireball ? Particle.EXPLOSION : Particle.EXPLOSION_EMITTER, location, 1);
                    viewer.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, EXPLOSION_VOLUME, EXPLOSION_PITCH);
                    entity.remove();
                }
            }
        }
    }

    /** A trail of sparks from the shooter's eyes to the first figure or wall in their line of sight. */
    private void shot(LivingEntity shooter) {
        var eye = shooter.getEyeLocation();
        var direction = eye.getDirection();
        var world = eye.getWorld();
        var hit = world.rayTrace(eye, direction, SHOT_RANGE, FluidCollisionMode.NEVER, true, 0,
                entity -> entity != shooter && entity instanceof LivingEntity && !(entity instanceof Player));
        double length = hit == null ? SHOT_RANGE : hit.getHitPosition().distance(eye.toVector());
        for (double step = 0.5; step < length; step += TRACER_SPACING) {
            world.spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(step)), 1, 0, 0, 0, 0);
        }
        viewer.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SHOT_VOLUME, SHOT_PITCH);
    }

    private void sound(Entity entity, Sound sound) {
        if (sound != null) {
            viewer.playSound(entity, sound, 1, 1);
        }
    }

    /** Stops projectiles and TNT where they are, for a picture that stays still. */
    void hold() {
        figures.values().stream().filter(figure -> drifts(figure.entity)).forEach(figure -> {
            figure.entity.setVelocity(new Vector());
            figure.velocity = null;
        });
    }

    Optional<Location> location(UUID id) {
        return Optional.ofNullable(figures.get(id)).map(figure -> figure.entity.getLocation());
    }

    /** The participants on screen, by name. */
    List<ReplayHeader.Participant> present() {
        return figures.keySet().stream().map(participants::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(ReplayHeader.Participant::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    void clear() {
        figures.values().forEach(figure -> figure.entity.remove());
        figures.clear();
        fixtures.values().forEach(fixture -> fixture.entity.remove());
        fixtures.clear();
    }
}
