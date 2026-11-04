package me.blockcraft.sternenfront.game;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

/**
 * Each faction's reinforcements: every death costs one, and the faction holding more posts drains the other by the
 * difference whenever the bleed comes round. A faction at zero has lost.
 */
public final class Reinforcements {

    private final Map<Faction, Integer> tickets = new EnumMap<>(Faction.class);

    public Reinforcements(int start) {
        for (var faction : Faction.values()) {
            tickets.put(faction, Math.max(1, start));
        }
    }

    public int left(Faction faction) {
        return tickets.get(faction);
    }

    public void lose(Faction faction, int amount) {
        tickets.computeIfPresent(faction, (ignored, left) -> Math.max(0, left - amount));
    }

    /** {@code owners} holds each post's owner; {@code weight} says what the post at an index counts for. */
    public void bleed(List<Optional<Faction>> owners, IntUnaryOperator weight) {
        int kolonien = weighed(owners, weight, Faction.KOLONIEN);
        int hegemonie = weighed(owners, weight, Faction.HEGEMONIE);
        if (kolonien != hegemonie) {
            lose(kolonien > hegemonie ? Faction.HEGEMONIE : Faction.KOLONIEN, Math.abs(kolonien - hegemonie));
        }
    }

    private static int weighed(List<Optional<Faction>> owners, IntUnaryOperator weight, Faction faction) {
        return IntStream.range(0, owners.size())
                .filter(index -> owners.get(index).filter(faction::equals).isPresent()).map(weight).sum();
    }

    public Optional<Faction> defeated() {
        return Arrays.stream(Faction.values()).filter(faction -> tickets.get(faction) == 0).findFirst();
    }

    /** The faction with more left, none on a tie. */
    public Optional<Faction> leader() {
        int kolonien = left(Faction.KOLONIEN);
        int hegemonie = left(Faction.HEGEMONIE);
        return kolonien == hegemonie ? Optional.empty()
                : Optional.of(kolonien > hegemonie ? Faction.KOLONIEN : Faction.HEGEMONIE);
    }
}
