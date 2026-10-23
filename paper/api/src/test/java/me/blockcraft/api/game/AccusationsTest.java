package me.blockcraft.api.game;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccusationsTest {

    @Test
    public void theWordsMatchWholeInAnyCase() {
        var words = Accusations.pattern(List.of("hacker", "hax", "cheatet"));

        assertTrue(words.matcher("Lisa ist ein HACKER!").find());
        assertTrue(words.matcher("hax").find());
        assertTrue(words.matcher("der cheatet doch").find());
        assertFalse(words.matcher("Hackerangriff auf die Burg").find());
        assertFalse(words.matcher("shax").find());
    }

    @Test
    public void withoutWordsNothingMatches() {
        assertFalse(Accusations.pattern(List.of()).matcher("hacker").find());
    }
}
