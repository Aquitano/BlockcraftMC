package me.blockcraft.sternenfront.game;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Where a fighter should go: the post to head for on the field, and the spawn to recommend while they deploy. */
public final class BattlePlan {

    public record Point(double x, double z) {
        public double distanceSquared(Point other) {
            return Math.pow(x - other.x, 2) + Math.pow(z - other.z, 2);
        }
    }

    /** {@code deployable} once the faction holds the post and no enemy keeps it from spawning there. */
    public record Post(int index, Point center, boolean deployable, boolean priority) { }

    private BattlePlan() { }

    /** The assault's priority, else the nearest post to take, else the post nearest the enemy to hold. */
    public static Optional<Post> postToHeadFor(List<Post> posts, Point origin, Point enemyHeadquarters) {
        return posts.stream().filter(Post::priority).findFirst()
                .or(() -> posts.stream().filter(post -> !post.deployable())
                        .min(Comparator.comparingDouble(post -> origin.distanceSquared(post.center()))))
                .or(() -> posts.stream().min(Comparator.comparingDouble(post -> enemyHeadquarters.distanceSquared(post.center()))));
    }

    public static Spawn recommend(List<Post> posts, Point headquarters, Point enemyHeadquarters, Optional<Spawn> remembered,
                                  Predicate<Spawn> available) {
        return remembered.filter(Spawn.Post.class::isInstance).filter(ignored -> posts.stream().noneMatch(Post::priority))
                .filter(available).orElseGet(() -> {
            var target = postToHeadFor(posts, headquarters, enemyHeadquarters).map(Post::center).orElse(enemyHeadquarters);
            return posts.stream().filter(Post::deployable)
                    .filter(post -> post.center().distanceSquared(target) < headquarters.distanceSquared(target))
                    .min(Comparator.comparingDouble(post -> post.center().distanceSquared(target)))
                    .<Spawn>map(post -> new Spawn.Post(post.index())).orElse(new Spawn.Headquarters());
        });
    }

    public static Spawn resolve(Spawn wanted, Predicate<Spawn> available, Spawn recommendation) {
        if (available.test(wanted)) return wanted;
        return available.test(recommendation) ? recommendation : new Spawn.Headquarters();
    }
}
