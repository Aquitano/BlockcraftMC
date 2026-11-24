package me.blockcraft.endergames.kit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** What spreads from one place through touching places of the same kind, such as the logs of a tree. */
final class Reach {

    private Reach() {
    }

    /** Everything {@code included} reachable from {@code start}, nearest first, at most {@code limit}; not start itself. */
    static <P> List<P> from(P start, Function<P, List<P>> neighbours, Predicate<P> included, int limit) {
        var found = new ArrayList<P>();
        var seen = new HashSet<P>();
        seen.add(start);
        var queue = new ArrayDeque<P>();
        queue.add(start);
        while (!queue.isEmpty() && found.size() < limit) {
            for (var next : neighbours.apply(queue.poll())) {
                if (found.size() >= limit) {
                    break;
                }
                if (seen.add(next) && included.test(next)) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }
}
