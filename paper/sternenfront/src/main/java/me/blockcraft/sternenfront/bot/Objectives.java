package me.blockcraft.sternenfront.bot;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

final class Objectives {

    record Post(int index, boolean owned, boolean contested, double distance, int assigned, double pull) { }

    static OptionalInt choose(List<Post> posts) {
        return posts.stream().min(Comparator.comparingDouble(Objectives::cost).thenComparingInt(Post::index))
                .map(post -> OptionalInt.of(post.index())).orElseGet(OptionalInt::empty);
    }

    private static double cost(Post post) {
        double priority = post.owned() ? post.contested() ? -40 : 80 : 0;
        return priority + post.distance() + post.assigned() * 35 - post.pull();
    }
}
