package me.blockcraft.story.cutscene;

import me.blockcraft.api.scene.CameraPath;
import me.blockcraft.api.scene.Ramp;
import me.blockcraft.story.game.Spots;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One cutscene from {@code cutscenes.scenes.<name>} in config.yml, timed in ticks: camera shots, the actors and the
 * gestures they make, subtitles, effects, craft flying past, ambient emitters and sounds.
 */
public record Cutscene(String name, CameraPath camera, Map<String, Actor> actors, List<Line> lines, List<Effect> effects,
                       List<Craft> craft, List<Emitter> emitters, List<Cue> sounds) {

    /** The cast member who already stands in the world and plays the lead; every other actor is spawned for the scene. */
    public static final String LEAD = "vela";

    /** Where a shot that follows an actor looks, in blocks above its feet: the chest, so the head sits above the middle. */
    static final double FOLLOW_HEIGHT = 1.3;

    public record Mark(int tick, Location at) { }

    /** An animation of the actor's model that starts at {@code tick}, or with {@code stop} ends there. */
    public record Gesture(int tick, String animation, boolean stop) { }

    /**
     * A cast member: its body's BetterModel model, the marks it walks through, the gestures and its idle animation.
     * Between two marks that lie apart it walks at an even pace that starts and stops over {@link #STEP_TICKS}, facing
     * where it goes and turning into and out of the walk at {@link #TURN_DEGREES_PER_TICK}; between two marks on one
     * spot it turns from the one's facing to the other's.
     */
    public record Actor(String model, List<Mark> marks, List<Gesture> gestures, String idle) {

        static final int STEP_TICKS = 8;
        static final float TURN_DEGREES_PER_TICK = 12;
        private static final double STILL = 0.1;

        public Optional<Location> at(int tick) {
            if (marks.isEmpty()) return Optional.empty();
            if (tick <= marks.getFirst().tick()) return Optional.of(marks.getFirst().at().clone());
            if (tick >= marks.getLast().tick()) return Optional.of(marks.getLast().at().clone());
            int i = 1;
            while (marks.get(i).tick() <= tick) i++;
            var from = marks.get(i - 1);
            var to = marks.get(i);
            int span = to.tick() - from.tick();
            int x = tick - from.tick();
            var path = to.at().toVector().subtract(from.at().toVector());
            double length = path.length();
            if (length < STILL) {
                double u = smooth((double) x / span);
                var at = from.at().clone();
                at.setYaw(from.at().getYaw() + turn(from.at().getYaw(), to.at().getYaw()) * (float) u);
                at.setPitch(from.at().getPitch() + (to.at().getPitch() - from.at().getPitch()) * (float) u);
                return Optional.of(at);
            }
            var at = from.at().clone().add(path.clone().multiply(Ramp.along(x, span, STEP_TICKS) / span));
            float heading = from.at().clone().setDirection(path).getYaw();
            float into = turn(from.at().getYaw(), heading);
            float out = turn(heading, to.at().getYaw());
            int turnIn = Math.min(turnTicks(into), span / 2);
            int turnOut = Math.min(turnTicks(out), span / 2);
            if (x < turnIn) {
                at.setYaw(from.at().getYaw() + into * (float) smooth((double) x / turnIn));
            } else if (x > span - turnOut) {
                at.setYaw(heading + out * (float) smooth((double) (x - span + turnOut) / turnOut));
            } else {
                at.setYaw(heading);
            }
            at.setPitch(from.at().getPitch() + (to.at().getPitch() - from.at().getPitch()) * (float) smooth((double) x / span));
            return Optional.of(at);
        }

        /** How far the actor moves from {@code tick} to the next, in blocks. */
        public double speed(int tick) {
            var here = at(tick);
            var next = at(tick + 1);
            return here.isPresent() && next.isPresent() ? here.get().distance(next.get()) : 0;
        }

        public boolean walking(int tick) {
            return speed(tick) > 0.005;
        }

        private static float turn(float from, float to) {
            return (float) Math.IEEEremainder(to - from, 360);
        }

        private static int turnTicks(float degrees) {
            return Math.max(1, Math.round(Math.abs(degrees) / TURN_DEGREES_PER_TICK));
        }
    }

    public record Line(int tick, int ticks, String speaker, String text) { }

    public enum EffectType { EXPLOSION, FLARE }

    /** An effect at {@code at}; an explosion with a {@code radius} tears the blocks around it out. */
    public record Effect(int tick, EffectType type, Location at, double radius) { }

    /** A ship's model flown from {@code from} to {@code to}, nose first, over {@code ticks}; one that {@code stays} waits there. */
    public record Craft(int tick, int ticks, String model, float scale, Location from, Location to, boolean stays) { }

    public enum EmitterType { SMOKE, DUST, SPARKS, EMBERS }

    /** Particles around {@code at} every {@code every} ticks for the whole scene. */
    public record Emitter(EmitterType type, Location at, int every) { }

    /** A sound at {@code tick}, at {@code at} or, without it, around the viewer. */
    public record Cue(int tick, String sound, float volume, float pitch, Optional<Location> at) { }

    public int duration() {
        return camera.duration();
    }

    /**
     * The ticks at which a cut takes the camera further than {@code blocks}, where the client has to load what it shows
     * next.
     */
    public List<Integer> farCuts(double blocks) {
        return camera.frames().stream().filter(CameraPath.Keyframe::cut).mapToInt(CameraPath.Keyframe::tick)
                .filter(cut -> {
                    var before = camera.sample(cut - 1);
                    var after = camera.sample(cut);
                    return Math.sqrt(Math.pow(after.x() - before.x(), 2) + Math.pow(after.y() - before.y(), 2)
                            + Math.pow(after.z() - before.z(), 2)) > blocks;
                }).boxed().toList();
    }

    /**
     * Each shot's keyframe looks from {@code at} towards {@code look}, or with {@code follow} at an actor
     * {@code height} blocks above its feet, {@link #FOLLOW_HEIGHT} unless given, wherever it is at each tick;
     * {@code cut: true} starts a new shot there.
     */
    public static Cutscene load(String name, World world, ConfigurationSection section) {
        var actors = new LinkedHashMap<String, Actor>();
        var cast = section.getConfigurationSection("actors");
        if (cast != null) {
            for (var key : cast.getKeys(false)) {
                var actor = cast.getConfigurationSection(key);
                actors.put(key, new Actor(actor.getString("model", ""),
                        actor.getMapList("marks").stream().map(mark -> new Mark(number(mark, "tick"), Spots.parse(world, text(mark, "at")))).toList(),
                        actor.getMapList("gestures").stream().map(gesture -> new Gesture(number(gesture, "tick"),
                                gesture.containsKey("stop") ? text(gesture, "stop") : text(gesture, "play"), gesture.containsKey("stop"))).toList(),
                        actor.getString("idle", "")));
            }
        }
        var shots = section.getMapList("shots").stream().map(shot -> {
            int tick = number(shot, "tick");
            var at = Spots.parse(world, text(shot, "at"));
            boolean cut = Boolean.TRUE.equals(shot.get("cut"));
            if (!(shot.get("follow") instanceof String follow)) return CameraPath.Keyframe.aimed(tick, at, Spots.parse(world, text(shot, "look")), cut);
            var actor = actors.get(follow);
            if (actor == null || actor.marks().isEmpty()) throw new IllegalArgumentException("No actor " + follow + " in " + name);
            double height = shot.get("height") instanceof Number value ? value.doubleValue() : FOLLOW_HEIGHT;
            return CameraPath.Keyframe.aimed(tick, at, moment -> CameraPath.Target.of(actor.at(moment).orElseThrow().add(0, height, 0)), cut);
        }).toList();
        var lines = section.getMapList("lines").stream()
                .map(line -> new Line(number(line, "tick"), number(line, "ticks"), text(line, "speaker"), text(line, "text"))).toList();
        var effects = section.getMapList("effects").stream()
                .map(effect -> new Effect(number(effect, "tick"), EffectType.valueOf(text(effect, "type")), Spots.parse(world, text(effect, "at")),
                        effect.get("radius") instanceof Number radius ? radius.doubleValue() : 0)).toList();
        var craft = section.getMapList("craft").stream()
                .map(ship -> new Craft(number(ship, "tick"), number(ship, "ticks"), text(ship, "model"),
                        ship.get("scale") instanceof Number scale ? scale.floatValue() : 1,
                        Spots.parse(world, text(ship, "from")), Spots.parse(world, text(ship, "to")),
                        Boolean.TRUE.equals(ship.get("stays")))).toList();
        var emitters = section.getMapList("emitters").stream()
                .map(emitter -> new Emitter(EmitterType.valueOf(text(emitter, "type")), Spots.parse(world, text(emitter, "at")),
                        Math.max(1, number(emitter, "every")))).toList();
        var sounds = section.getMapList("sounds").stream()
                .map(cue -> new Cue(number(cue, "tick"), text(cue, "sound"),
                        cue.get("volume") instanceof Number volume ? volume.floatValue() : 1,
                        cue.get("pitch") instanceof Number pitch ? pitch.floatValue() : 1,
                        cue.get("at") instanceof String at ? Optional.of(Spots.parse(world, at)) : Optional.empty())).toList();
        return new Cutscene(name, new CameraPath(shots), Map.copyOf(actors), lines, effects, craft, emitters, sounds);
    }

    public Optional<Line> lineAt(int tick) {
        return lines.stream().filter(line -> tick >= line.tick() && tick < line.tick() + line.ticks()).findFirst();
    }

    private static double smooth(double progress) {
        return progress * progress * (3 - 2 * progress);
    }

    private static String text(Map<?, ?> values, String key) {
        if (values.get(key) instanceof String value) return value;
        throw new IllegalArgumentException("Missing '" + key + "' in " + values);
    }

    private static int number(Map<?, ?> values, String key) {
        if (values.get(key) instanceof Number value) return value.intValue();
        throw new IllegalArgumentException("Missing '" + key + "' in " + values);
    }
}
