package me.blockcraft.sternenfront.secret;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The Kessel-Lauf board: every runner once with their best time, fastest first, at most {@code size} of them. */
record BestTimes(List<Entry> entries, int size) {

    record Entry(UUID player, String name, long ticks) {
    }

    BestTimes {
        entries = List.copyOf(entries);
    }

    /** The board after {@code run}: a runner's slower run leaves their best alone, a faster one replaces it. */
    BestTimes with(Entry run) {
        var best = new ArrayList<Entry>();
        boolean faster = true;
        for (var entry : entries) {
            if (!entry.player().equals(run.player())) {
                best.add(entry);
            } else if (entry.ticks() <= run.ticks()) {
                best.add(entry);
                faster = false;
            }
        }
        if (faster) {
            best.add(run);
        }
        best.sort(Comparator.comparingLong(Entry::ticks));
        return new BestTimes(best.subList(0, Math.min(size, best.size())), size);
    }
}
