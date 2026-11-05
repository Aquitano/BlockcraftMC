package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.TargetShotEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Brummi, a small power droid that waddles round a stall of the Basar, stops at every corner and beeps now and then.
 * Bolts stop at it; a hit makes it jump and beep. The third hit from one player in a round sends it off squealing at a
 * run, finds that player the secret, and keeps it out of sight for a while before it waddles back. Its hitbox is an
 * interaction, so it never stands in anyone's way and takes no damage.
 */
final class Brummi implements Listener {

    private static final double WALK = 0.045;
    private static final double RUN = 0.22;
    private static final int CORNER_TICKS = 24;
    private static final int RUN_TICKS = 50;
    private static final int STEP_TICKS = 2;
    private static final int SWAY_TICKS = 6;
    private static final float SWAY = (float) Math.toRadians(7);
    private static final double HEIGHT = 0.5;

    private final Plugin plugin;
    private final Messages messages;
    private final Discoveries discoveries;
    private final List<Location> path;
    private final int hitsToPanic;
    private final long hideTicks;
    private final Map<UUID, Integer> hits = new HashMap<>();
    private Optional<ItemDisplay> display = Optional.empty();
    private Optional<Interaction> hitbox = Optional.empty();
    private int leg;
    private double along;
    private long waitUntil;
    private long fleeUntil;
    private long hiddenUntil;
    private long hopUntil;
    private long nextBeep;
    private boolean swayLeft;

    Brummi(Plugin plugin, Messages messages, Discoveries discoveries, List<Location> path, int hitsToPanic, int hideSeconds) {
        this.plugin = plugin;
        this.messages = messages;
        this.discoveries = discoveries;
        this.path = path;
        this.hitsToPanic = hitsToPanic;
        this.hideTicks = hideSeconds * 20L;
        path.forEach(point -> point.getChunk().addPluginChunkTicket(plugin));
    }

    void tick(long tick) {
        if (display.isEmpty()) {
            if (tick >= hiddenUntil) {
                appear();
            }
            return;
        }
        boolean fleeing = tick < fleeUntil;
        if (fleeUntil > 0 && !fleeing) {
            vanish(tick);
            return;
        }
        if (fleeing || tick >= waitUntil) {
            walk(fleeing ? RUN : WALK, tick);
        }
        if (tick % STEP_TICKS == 0) {
            show(tick, fleeing);
        }
        if (!fleeing && tick >= nextBeep) {
            SoundCue.BRUMMI_BEEP.play(position(), 0.7f);
            nextBeep = tick + ThreadLocalRandom.current().nextLong(120, 260);
        }
    }

    private void walk(double speed, long tick) {
        var from = path.get(leg).toVector();
        var to = path.get((leg + 1) % path.size()).toVector();
        along += speed;
        double length = from.distance(to);
        if (along >= length) {
            along -= length;
            leg = (leg + 1) % path.size();
            if (tick >= fleeUntil) {
                waitUntil = tick + CORNER_TICKS;
                along = 0;
            }
        }
    }

    private Location position() {
        var from = path.get(leg);
        var to = path.get((leg + 1) % path.size());
        var step = to.toVector().subtract(from.toVector());
        double length = step.length();
        var at = from.clone().add(step.clone().multiply(length == 0 ? 0 : along / length));
        at.setDirection(step.lengthSquared() == 0 ? new Vector(0, 0, 1) : step);
        at.setPitch(0);
        return at;
    }

    private void show(long tick, boolean fleeing) {
        var at = position();
        display.ifPresent(shown -> {
            shown.teleport(at.clone().add(0, HEIGHT, 0));
            boolean moving = fleeing || tick >= waitUntil;
            if (tick % (fleeing ? STEP_TICKS : SWAY_TICKS) == 0) {
                swayLeft = !swayLeft;
                shown.setInterpolationDelay(0);
                shown.setInterpolationDuration(fleeing ? STEP_TICKS : SWAY_TICKS);
                shown.setTransformation(pose(moving ? (swayLeft ? SWAY : -SWAY) : 0, tick < hopUntil));
            }
        });
        hitbox.ifPresent(box -> box.teleport(at));
        if (fleeing) {
            at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 0.4, 0), 3, 0.15, 0.1, 0.15, 0.01);
        }
    }

    private static Transformation pose(float roll, boolean hop) {
        return new Transformation(new Vector3f(0, hop ? 0.45f : 0, 0), new AxisAngle4f(roll, 0, 0, 1), new Vector3f(1),
                new AxisAngle4f());
    }

    private void appear() {
        leg = 0;
        along = 0;
        fleeUntil = 0;
        var at = position();
        display = Optional.of(Displays.item(at.clone().add(0, HEIGHT, 0), PackModel.STERNENFRONT_BRUMMI.item().build(),
                shown -> shown.setTeleportDuration(STEP_TICKS)));
        hitbox = Optional.of(at.getWorld().spawn(at, Interaction.class, box -> {
            box.setPersistent(false);
            box.setInteractionWidth(0.8f);
            box.setInteractionHeight(0.9f);
            box.setResponsive(false);
            box.getPersistentDataContainer().set(TargetShotEvent.MARK, PersistentDataType.BOOLEAN, true);
        }));
    }

    private void vanish(long tick) {
        var at = position();
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.02);
        display.ifPresent(ItemDisplay::remove);
        hitbox.ifPresent(Interaction::remove);
        display = Optional.empty();
        hitbox = Optional.empty();
        hiddenUntil = tick + hideTicks;
    }

    @EventHandler
    public void onShot(TargetShotEvent event) {
        if (hitbox.filter(event.target()::equals).isEmpty() || fleeUntil > 0) {
            return;
        }
        var shooter = event.shooter();
        var at = position();
        long tick = plugin.getServer().getCurrentTick();
        int count = hits.merge(shooter.getUniqueId(), 1, Integer::sum);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.6, 0), 10, 0.2, 0.2, 0.2, 0.05);
        if (count < hitsToPanic) {
            hopUntil = tick + SWAY_TICKS;
            display.ifPresent(shown -> {
                shown.setInterpolationDelay(0);
                shown.setInterpolationDuration(STEP_TICKS);
                shown.setTransformation(pose(0, true));
            });
            SoundCue.BRUMMI_BEEP.play(at, 1f);
            BlockcraftApi.get().notifications().feedback(shooter, messages.get("secret.brummi-hit"));
            return;
        }
        hits.remove(shooter.getUniqueId());
        fleeUntil = tick + RUN_TICKS;
        SoundCue.BRUMMI_SCARED.play(at, 1.5f);
        BlockcraftApi.get().notifications().feedback(shooter, messages.get("secret.brummi-panic"));
        discoveries.find(shooter, Secret.BRUMMI);
    }

    @EventHandler
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.phase() == GamePhase.INGAME) {
            hits.clear();
        }
    }

    void close() {
        display.ifPresent(ItemDisplay::remove);
        hitbox.ifPresent(Interaction::remove);
        display = Optional.empty();
        hitbox = Optional.empty();
        hits.clear();
        path.forEach(point -> point.getChunk().removePluginChunkTicket(plugin));
    }
}
