package me.blockcraft.sternenfront.game;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** The assault's Schwerpunkt: the post that counts for more, and the faction that trails and may land next to it. */
public record PriorityPost(Faction trailing, int post) {

    private static final int CENTRE = 1;

    /** {@code owners} holds each post's owner, A first. */
    public static PriorityPost pick(Reinforcements tickets, List<Optional<Faction>> owners) {
        var trailing = trailing(tickets, owners);
        int post = nearestCentre(owners, Optional.of(trailing.enemy()))
                .or(() -> nearestCentre(owners, Optional.empty()))
                .orElse(CENTRE);
        return new PriorityPost(trailing, post);
    }

    /** Fewer tickets, on a tie fewer posts, and with both tied the Kolonien, the first faction. */
    public static Faction trailing(Reinforcements tickets, List<Optional<Faction>> owners) {
        return List.of(Faction.values()).stream()
                .min(Comparator.comparingInt(tickets::left)
                        .thenComparingLong(faction -> owners.stream().filter(Optional.of(faction)::equals).count()))
                .orElseThrow();
    }

    private static Optional<Integer> nearestCentre(List<Optional<Faction>> owners, Optional<Faction> owner) {
        return IntStream.range(0, owners.size()).filter(index -> owners.get(index).equals(owner)).boxed()
                .min(Comparator.comparingInt(index -> Math.abs(index - CENTRE)));
    }
}
