package me.blockcraft.lobby.konami;

import me.blockcraft.lobby.konami.KonamiCode.Step;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static me.blockcraft.lobby.konami.KonamiKey.DOWN;
import static me.blockcraft.lobby.konami.KonamiKey.LEFT;
import static me.blockcraft.lobby.konami.KonamiKey.UP;
import static org.junit.Assert.assertEquals;

public class KonamiCodeTest {

    private static final Duration GAP = Duration.ofMillis(1500);
    private static final long STEP = Duration.ofMillis(300).toNanos();

    private final KonamiCode code = new KonamiCode(GAP);
    private long now = Duration.ofHours(1).toNanos();

    @Test
    public void theWholeSequenceCountsUpAndCompletes() {
        var steps = new ArrayList<Step>();
        KonamiCode.SEQUENCE.forEach(key -> steps.add(press(key)));

        var expected = new ArrayList<Step>();
        for (int matched = 1; matched < KonamiCode.SEQUENCE.size(); matched++) {
            expected.add(new Step.Advanced(matched));
        }
        expected.add(new Step.Completed());
        assertEquals(expected, steps);
        assertEquals(new Step.Advanced(1), press(UP));
    }

    @Test
    public void aWrongKeyReportsTheRunItBrokeAndKeepsWhatStillStartsTheCode() {
        List.of(UP, UP, DOWN, DOWN).forEach(this::press);
        assertEquals(new Step.Missed(4), press(UP));
        assertEquals(new Step.Advanced(2), press(UP));
        assertEquals(new Step.Missed(2), press(UP));
        assertEquals(new Step.Advanced(3), press(DOWN));
        assertEquals(new Step.Missed(3), press(LEFT));
        assertEquals(new Step.Missed(0), press(DOWN));
    }

    @Test
    public void aPauseLongerThanTheGapStartsOver() {
        List.of(UP, UP, DOWN).forEach(this::press);
        now += GAP.toNanos();
        assertEquals(new Step.Advanced(4), code.press(DOWN, now));
        now += GAP.toNanos() + 1;
        assertEquals(new Step.Missed(0), code.press(LEFT, now));
        assertEquals(new Step.Advanced(1), press(UP));
    }

    private Step press(KonamiKey key) {
        now += STEP;
        return code.press(key, now);
    }
}
