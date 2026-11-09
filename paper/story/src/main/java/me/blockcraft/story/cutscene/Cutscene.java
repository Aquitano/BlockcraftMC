package me.blockcraft.story.cutscene;

import me.blockcraft.api.scene.CameraPath;
import me.blockcraft.story.game.Spots;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One cutscene from {@code cutscenes.<name>} in config.yml: camera shots that look from {@code at} towards
 * {@code look}, the walk of its actor, the subtitles and the effects it sets off, all timed in ticks.
 */
public record Cutscene(String name, CameraPath camera, List<Mark> actor, List<Line> lines, List<Effect> effects) {

    public record Mark(int tick, Location at) { }

    public record Line(int tick, int ticks, String speaker, String text) { }

    public enum EffectType { EXPLOSION, FLARE }

    public record Effect(int tick, EffectType type, Location at) { }

    public int duration() {
        return camera.duration();
    }

    public static Cutscene load(String name, World world, ConfigurationSection section) {
        var shots = section.getMapList("shots").stream().map(shot -> {
            var at = Spots.parse(world, text(shot, "at"));
            var look = Spots.parse(world, text(shot, "look"));
            at.setDirection(look.toVector().subtract(at.toVector()));
            return new CameraPath.Keyframe(number(shot, "tick"), CameraPath.Pose.of(at), Boolean.TRUE.equals(shot.get("cut")));
        }).toList();
        var actor = section.getMapList("actor").stream()
                .map(mark -> new Mark(number(mark, "tick"), Spots.parse(world, text(mark, "at")))).toList();
        var lines = section.getMapList("lines").stream()
                .map(line -> new Line(number(line, "tick"), number(line, "ticks"), text(line, "speaker"), text(line, "text"))).toList();
        var effects = section.getMapList("effects").stream()
                .map(effect -> new Effect(number(effect, "tick"), EffectType.valueOf(text(effect, "type")), Spots.parse(world, text(effect, "at")))).toList();
        return new Cutscene(name, new CameraPath(shots), actor, lines, effects);
    }

    /** Where the actor stands at {@code tick}: walking straight between its marks and turning to each mark's facing. */
    public Optional<Location> actorAt(int tick) {
        if (actor.isEmpty()) return Optional.empty();
        if (tick <= actor.getFirst().tick()) return Optional.of(actor.getFirst().at().clone());
        for (int i = 1; i < actor.size(); i++) {
            var to = actor.get(i);
            if (tick < to.tick()) {
                var from = actor.get(i - 1);
                double progress = (double) (tick - from.tick()) / (to.tick() - from.tick());
                var at = from.at().clone().add(to.at().toVector().subtract(from.at().toVector()).multiply(progress));
                float turn = (float) Math.IEEEremainder(to.at().getYaw() - from.at().getYaw(), 360);
                at.setYaw(from.at().getYaw() + turn * (float) Math.min(1, progress * 4));
                at.setPitch(from.at().getPitch() + (to.at().getPitch() - from.at().getPitch()) * (float) progress);
                return Optional.of(at);
            }
        }
        return Optional.of(actor.getLast().at().clone());
    }

    public Optional<Line> lineAt(int tick) {
        return lines.stream().filter(line -> tick >= line.tick() && tick < line.tick() + line.ticks()).findFirst();
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
