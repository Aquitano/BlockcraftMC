package me.blockcraft.story.cutscene;

import me.blockcraft.api.scene.CameraPath;
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

    public record Mark(int tick, Location at) { }

    /** An animation of the actor's model that starts at {@code tick}, or with {@code stop} ends there. */
    public record Gesture(int tick, String animation, boolean stop) { }

    /** A cast member: its body's BetterModel model, the marks it walks through, the gestures and its idle animation. */
    public record Actor(String model, List<Mark> marks, List<Gesture> gestures, String idle) {

        /** Where the actor stands at {@code tick}: walking straight between its marks and turning to each mark's facing. */
        public Optional<Location> at(int tick) {
            if (marks.isEmpty()) return Optional.empty();
            if (tick <= marks.getFirst().tick()) return Optional.of(marks.getFirst().at().clone());
            for (int i = 1; i < marks.size(); i++) {
                var to = marks.get(i);
                if (tick < to.tick()) {
                    var from = marks.get(i - 1);
                    double progress = smooth((double) (tick - from.tick()) / (to.tick() - from.tick()));
                    var at = from.at().clone().add(to.at().toVector().subtract(from.at().toVector()).multiply(progress));
                    float turn = (float) Math.IEEEremainder(to.at().getYaw() - from.at().getYaw(), 360);
                    boolean walking = from.at().distanceSquared(to.at()) > 0.01;
                    double facing = walking ? Math.min(1, progress * 4) : progress;
                    at.setYaw(from.at().getYaw() + turn * (float) facing);
                    at.setPitch(from.at().getPitch() + (to.at().getPitch() - from.at().getPitch()) * (float) progress);
                    return Optional.of(at);
                }
            }
            return Optional.of(marks.getLast().at().clone());
        }

        /** Whether the actor is between two marks that lie apart at {@code tick}. */
        public boolean walking(int tick) {
            for (int i = 1; i < marks.size(); i++) {
                if (tick >= marks.get(i - 1).tick() && tick < marks.get(i).tick()) {
                    return marks.get(i - 1).at().distanceSquared(marks.get(i).at()) > 0.01;
                }
            }
            return false;
        }
    }

    public record Line(int tick, int ticks, String speaker, String text) { }

    public enum EffectType { EXPLOSION, FLARE }

    /** An effect at {@code at}; an explosion with a {@code radius} tears the blocks around it out. */
    public record Effect(int tick, EffectType type, Location at, double radius) { }

    /** A ship's model flown from {@code from} to {@code to}, nose first, over {@code ticks}. */
    public record Craft(int tick, int ticks, String model, float scale, Location from, Location to) { }

    public enum EmitterType { SMOKE, DUST, SPARKS, EMBERS }

    /** Particles around {@code at} every {@code every} ticks for the whole scene. */
    public record Emitter(EmitterType type, Location at, int every) { }

    /** A sound at {@code tick}, at {@code at} or, without it, around the viewer. */
    public record Cue(int tick, String sound, float volume, float pitch, Optional<Location> at) { }

    public int duration() {
        return camera.duration();
    }

    /**
     * Each shot's keyframe looks from {@code at} towards {@code look}, or towards an actor's head at that tick with
     * {@code follow}; {@code cut: true} starts a new shot there.
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
            var look = shot.get("follow") instanceof String follow
                    ? head(actors.get(follow), tick).orElseThrow(() -> new IllegalArgumentException("No actor " + follow + " in " + name))
                    : Spots.parse(world, text(shot, "look"));
            return CameraPath.Keyframe.aimed(tick, at, look, Boolean.TRUE.equals(shot.get("cut")));
        }).toList();
        var lines = section.getMapList("lines").stream()
                .map(line -> new Line(number(line, "tick"), number(line, "ticks"), text(line, "speaker"), text(line, "text"))).toList();
        var effects = section.getMapList("effects").stream()
                .map(effect -> new Effect(number(effect, "tick"), EffectType.valueOf(text(effect, "type")), Spots.parse(world, text(effect, "at")),
                        effect.get("radius") instanceof Number radius ? radius.doubleValue() : 0)).toList();
        var craft = section.getMapList("craft").stream()
                .map(ship -> new Craft(number(ship, "tick"), number(ship, "ticks"), text(ship, "model"),
                        ship.get("scale") instanceof Number scale ? scale.floatValue() : 1,
                        Spots.parse(world, text(ship, "from")), Spots.parse(world, text(ship, "to")))).toList();
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

    private static Optional<Location> head(Actor actor, int tick) {
        return Optional.ofNullable(actor).flatMap(cast -> cast.at(tick)).map(at -> at.add(0, 1.6, 0));
    }

    public Optional<Line> lineAt(int tick) {
        return lines.stream().filter(line -> tick >= line.tick() && tick < line.tick() + line.ticks()).findFirst();
    }

    /** Eases a walk in and out, so an actor starts and stops rather than gliding at one speed. */
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
