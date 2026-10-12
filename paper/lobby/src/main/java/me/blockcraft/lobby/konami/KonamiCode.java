package me.blockcraft.lobby.konami;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static me.blockcraft.lobby.konami.KonamiKey.A;
import static me.blockcraft.lobby.konami.KonamiKey.B;
import static me.blockcraft.lobby.konami.KonamiKey.DOWN;
import static me.blockcraft.lobby.konami.KonamiKey.LEFT;
import static me.blockcraft.lobby.konami.KonamiKey.RIGHT;
import static me.blockcraft.lobby.konami.KonamiKey.UP;

/**
 * How far one player has come through the code. Every press must follow the last one within the gap, or the run
 * starts over. A wrong key keeps the longest tail of the presses that still starts the code, so a third tap of up
 * keeps two.
 */
public final class KonamiCode {

    public static final List<KonamiKey> SEQUENCE = List.of(UP, UP, DOWN, DOWN, LEFT, RIGHT, LEFT, RIGHT, B, A);

    public sealed interface Step {

        /** The press was right and {@code matched} keys of the code are done. */
        record Advanced(int matched) implements Step {
        }

        record Completed() implements Step {
        }

        /** The press broke a run of {@code had} matched keys. */
        record Missed(int had) implements Step {
        }
    }

    private final long gapNanos;
    private int matched;
    private long lastPress;

    public KonamiCode(Duration gap) {
        gapNanos = gap.toNanos();
    }

    public Step press(KonamiKey key, long nanoTime) {
        if (nanoTime - lastPress > gapNanos) {
            matched = 0;
        }
        lastPress = nanoTime;
        int had = matched;
        if (SEQUENCE.get(had) != key) {
            matched = restart(had, key);
            return new Step.Missed(had);
        }
        if (had + 1 == SEQUENCE.size()) {
            matched = 0;
            return new Step.Completed();
        }
        matched = had + 1;
        return new Step.Advanced(matched);
    }

    private static int restart(int had, KonamiKey key) {
        var presses = new ArrayList<>(SEQUENCE.subList(0, had));
        presses.add(key);
        for (int length = had; length > 0; length--) {
            if (presses.subList(presses.size() - length, presses.size()).equals(SEQUENCE.subList(0, length))) {
                return length;
            }
        }
        return 0;
    }
}
