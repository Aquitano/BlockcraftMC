package me.blockcraft.sternenfront.secret;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CantinaTuneTest {

    @Test
    public void everyPartLastsTheWholeTune() {
        for (var voice : CantinaTune.VOICES) {
            assertEquals(voice.instrument().name(), CantinaTune.BAR * CantinaTune.BARS, CantinaTune.length(voice));
        }
    }

    @Test
    public void everyNoteLiesInTheTwoOctavesItsInstrumentCanPlay() {
        for (var note : CantinaTune.NOTES) {
            assertTrue(note + " is out of range", note.pitch() >= 0.5f - 1e-4f && note.pitch() <= 2f + 1e-4f);
        }
    }

    @Test
    public void noteNamesCountFromMiddleC() {
        assertEquals(60, CantinaTune.midi("C4"));
        assertEquals(61, CantinaTune.midi("C#4"));
        assertEquals(46, CantinaTune.midi("Bb2"));
    }
}
