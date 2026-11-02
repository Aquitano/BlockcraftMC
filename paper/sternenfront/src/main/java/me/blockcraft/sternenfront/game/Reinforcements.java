package me.blockcraft.sternenfront.game;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

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

    /** {@code held} counts the posts each faction holds; one it lacks holds none. */
    public void bleed(Map<Faction, Long> held) {
        long kolonien = held.getOrDefault(Faction.KOLONIEN, 0L);
        long hegemonie = held.getOrDefault(Faction.HEGEMONIE, 0L);
        if (kolonien != hegemonie) {
            lose(kolonien > hegemonie ? Faction.HEGEMONIE : Faction.KOLONIEN, (int) Math.abs(kolonien - hegemonie));
        }
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
