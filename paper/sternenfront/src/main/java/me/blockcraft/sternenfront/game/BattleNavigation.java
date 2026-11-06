package me.blockcraft.sternenfront.game;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class BattleNavigation {

    public record Point(double x, double z) {
        public double distanceSquared(Point other) {
            return Math.pow(x - other.x, 2) + Math.pow(z - other.z, 2);
        }
    }

    public record Post(int index, Point center, boolean safe, boolean objective) { }

    private BattleNavigation() { }

    public static Optional<Post> target(List<Post> posts, Point origin, Point enemyHeadquarters) {
        return posts.stream().filter(Post::objective).min(Comparator.comparingDouble(post -> origin.distanceSquared(post.center())))
                .or(() -> posts.stream().min(Comparator.comparingDouble(post -> enemyHeadquarters.distanceSquared(post.center()))));
    }

    public static Spawn recommend(List<Post> posts, Point headquarters, Point enemyHeadquarters, Optional<Spawn> remembered,
                                  Predicate<Spawn> available) {
        return remembered.filter(spawn -> !(spawn instanceof Spawn.Dropship)).filter(available).orElseGet(() -> {
            var target = target(posts, headquarters, enemyHeadquarters).map(Post::center).orElse(enemyHeadquarters);
            return posts.stream().filter(Post::safe)
                    .filter(post -> post.center().distanceSquared(target) < headquarters.distanceSquared(target))
                    .min(Comparator.comparingDouble(post -> post.center().distanceSquared(target)))
                    .<Spawn>map(post -> new Spawn.Post(post.index())).orElse(new Spawn.Headquarters());
        });
    }

    public static Spawn resolve(Spawn wanted, Predicate<Spawn> available, Spawn recommendation) {
        if (available.test(wanted)) return wanted;
        return available.test(recommendation) ? recommendation : new Spawn.Headquarters();
    }

    public static String direction(Point origin, float yaw, Point target) {
        double bearing = Math.toDegrees(Math.atan2(-(target.x() - origin.x()), target.z() - origin.z()));
        double relative = ((bearing - yaw) % 360 + 540) % 360 - 180;
        if (Math.abs(relative) <= 45) return "ahead";
        if (Math.abs(relative) >= 135) return "behind";
        return relative > 0 ? "right" : "left";
    }
}
