package me.blockcraft.sternenfront.bot;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

final class Objectives {

    enum Hold { OPEN, THREATENED, HELD }

    /** {@code assigned} counts the other fireteams already going there; {@code fromEnemy} is the walk from the enemy's headquarters. */
    record Post(int index, Hold hold, double distance, double fromEnemy, int assigned, double pull) { }

    private static final double SPREAD = 35;
    private static final double REINFORCE = 40;

    private Objectives() { }

    /**
     * A post the team can still fight for, keeping {@code current} unless another is more than {@code margin} blocks
     * cheaper. Once every post is held, the team holds the one the enemy reaches first.
     */
    static OptionalInt choose(List<Post> posts, int current, double margin) {
        var contestable = posts.stream().filter(post -> post.hold() != Hold.HELD).toList();
        if (contestable.isEmpty()) {
            return posts.stream().min(Comparator.comparingDouble((Post post) -> post.fromEnemy() + post.assigned() * SPREAD
                    - post.pull()).thenComparingInt(Post::index)).map(post -> OptionalInt.of(post.index())).orElseGet(OptionalInt::empty);
        }
        var best = contestable.stream().min(Comparator.comparingDouble(Objectives::cost).thenComparingInt(Post::index)).orElseThrow();
        return OptionalInt.of(contestable.stream().filter(post -> post.index() == current)
                .filter(post -> cost(post) <= cost(best) + margin).findFirst().orElse(best).index());
    }

    private static double cost(Post post) {
        return post.distance() + post.assigned() * SPREAD - post.pull() - (post.hold() == Hold.THREATENED ? REINFORCE : 0);
    }
}
