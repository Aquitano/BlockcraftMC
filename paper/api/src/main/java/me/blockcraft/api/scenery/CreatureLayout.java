package me.blockcraft.api.scenery;

import me.blockcraft.api.world.Point;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record CreatureLayout(int stepTicks, List<Creature> creatures) {

    sealed interface Creature {
        String id();
        Point pose(long ticks);
    }

    record BeeFlight(String id, List<Point> route, int travelTicks, int restTicks, int phaseTicks,
                     double hover) implements Creature {
        @Override
        public Point pose(long ticks) {
            long legTicks = (long) travelTicks + restTicks;
            long cycle = Math.floorMod(ticks + phaseTicks, legTicks * route.size());
            int leg = (int) (cycle / legTicks);
            var from = route.get(leg);
            var to = route.get((leg + 1) % route.size());
            double progress = Math.clamp((double) (cycle % legTicks - restTicks) / travelTicks, 0, 1);
            double eased = progress * progress * (3 - 2 * progress);
            double dx = to.x() - from.x();
            double dz = to.z() - from.z();
            return new Point(from.x() + dx * eased,
                    from.y() + (to.y() - from.y()) * eased + hover * Math.sin(2 * Math.PI * (cycle % legTicks) / legTicks),
                    from.z() + dz * eased, (float) Math.toDegrees(Math.atan2(-dx, dz)), 0);
        }
    }

    record PerchedBird(String id, Point at, int cycleTicks, int lookTicks, int hopTicks, double hopHeight,
                       float lookDegrees) implements Creature {
        @Override
        public Point pose(long ticks) {
            long cycle = Math.floorMod(ticks, cycleTicks);
            float yaw = at.yaw();
            if (cycle < lookTicks) {
                yaw += (float) (lookDegrees * Math.sin(2 * Math.PI * cycle / lookTicks));
            }
            long hop = cycle - lookTicks;
            double lift = hop >= 0 && hop < hopTicks ? hopHeight * Math.sin(Math.PI * hop / hopTicks) : 0;
            return new Point(at.x(), at.y() + lift, at.z(), yaw, at.pitch());
        }
    }

    static CreatureLayout load(Path file) {
        return parse(YamlConfiguration.loadConfiguration(file.toFile()));
    }

    static CreatureLayout parse(ConfigurationSection yaml) {
        int step = integer(yaml, "step-ticks", 1, 20);
        var result = new ArrayList<Creature>();
        var creatures = yaml.getConfigurationSection("creatures");
        if (creatures != null) {
            for (var id : creatures.getKeys(false)) {
                var entry = creatures.getConfigurationSection(id);
                if (entry == null) {
                    throw new IllegalArgumentException("Invalid creature: " + id);
                }
                switch (entry.getString("type", "")) {
                    case "bee" -> {
                        var route = entry.getMapList("route").stream().map(CreatureLayout::point).toList();
                        if (route.size() < 2) {
                            throw new IllegalArgumentException("Bee " + id + " needs at least two route points");
                        }
                        result.add(new BeeFlight(id, route, integer(entry, "travel-ticks", step, 12000),
                                integer(entry, "rest-ticks", step, 12000), integer(entry, "phase-ticks", 0, 12000),
                                number(entry, "hover-blocks", 0, 0.25)));
                    }
                    case "bird" -> {
                        var at = entry.getConfigurationSection("at");
                        if (at == null) {
                            throw new IllegalArgumentException("Bird " + id + " needs a perch");
                        }
                        int cycle = integer(entry, "cycle-ticks", step, 12000);
                        int look = integer(entry, "look-ticks", step, cycle);
                        int hop = integer(entry, "hop-ticks", step, cycle);
                        if (look + hop >= cycle) {
                            throw new IllegalArgumentException("Bird " + id + " needs time to rest between movements");
                        }
                        result.add(new PerchedBird(id, point(at.getValues(false)), cycle, look, hop,
                                number(entry, "hop-height", 0, 1), (float) number(entry, "look-degrees", 0, 90)));
                    }
                    default -> throw new IllegalArgumentException("Unknown creature type at " + id);
                }
            }
        }
        return new CreatureLayout(step, List.copyOf(result));
    }

    private static Point point(java.util.Map<?, ?> values) {
        var point = Point.parse(values);
        if (!Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z())
                || !Float.isFinite(point.yaw()) || !Float.isFinite(point.pitch())) {
            throw new IllegalArgumentException("Creature positions must be finite");
        }
        return point;
    }

    private static int integer(ConfigurationSection entry, String key, int min, int max) {
        double value = number(entry, key, min, max);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException(key + " must be an integer at " + entry.getCurrentPath());
        }
        return (int) value;
    }

    private static double number(ConfigurationSection entry, String key, double min, double max) {
        if (!(entry.get(key) instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
            throw new IllegalArgumentException("Invalid " + key + " at " + entry.getCurrentPath());
        }
        return number.doubleValue();
    }
}
