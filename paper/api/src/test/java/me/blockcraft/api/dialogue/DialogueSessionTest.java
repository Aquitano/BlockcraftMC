package me.blockcraft.api.dialogue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DialogueSessionTest {
    @Test
    public void advancingRevealsThenPagesThenFollowsTheSelectedBranch() {
        var dialogue = new Dialogue("briefing", Map.of(
                "briefing", new Dialogue.Line("speaker", "one\ntwo\nthree\nfour", Dialogue.Portrait.PILOT, "",
                        List.of(new Dialogue.Choice("left", "left"), new Dialogue.Choice("right", "right"))),
                "left", new Dialogue.Line("speaker", "left ending", Dialogue.Portrait.PILOT_HAPPY, "", List.of()),
                "right", new Dialogue.Line("speaker", "right ending", Dialogue.Portrait.PILOT_WORRIED, "", List.of())));
        var session = new DialogueSession(dialogue, key -> key);
        session.reveal(2);
        assertEquals(List.of("on", "", ""), session.visibleLines());
        session.advance();
        assertEquals(List.of("one", "two", "three"), session.visibleLines());
        assertFalse(session.choosing());
        session.advance();
        assertEquals(1, session.page());
        session.advance();
        assertTrue(session.choosing());
        session.select(-1);
        assertEquals(1, session.choice());
        session.advance();
        assertEquals("right", session.lineId());
        assertFalse(session.finished());
        session.advance();
        assertFalse(session.finished());
        session.advance();
        assertTrue(session.finished());
        session.advance();
        assertEquals("right", session.lineId());
    }

    @Test
    public void longWordsAndUnicodeCannotOverflowThePanelOrSplitSurrogatePairs() {
        String text = "Äußere\u00a0Decks 😄\n" + "abcdefghij".repeat(20);
        var pages = DialogueText.pages(text, 222);
        assertTrue(pages.size() > 1);
        for (var page : pages) {
            assertTrue(page.size() <= 3);
            for (String line : page) assertTrue(DialogueText.width(line) <= 222);
        }
        assertEquals("Äußere Decks ?", pages.getFirst().getFirst());
        assertTrue(DialogueText.width(DialogueText.fit(text, 40)) <= 40);
    }

    @Test
    public void invalidGraphReportsTargetsAndAmbiguousBranchesBeforePlayback() {
        var loaded = Dialogue.load(Map.of("first", "start", "lines", Map.of("start", Map.of(
                "speaker", "speaker", "text", "text", "portrait", "UNKNOWN", "next", "missing",
                "choices", List.of(Map.of("text", "choice", "next", "other"))))));
        assertFalse(loaded.valid());
        assertTrue(loaded.problems().stream().anyMatch(problem -> problem.contains("Unknown portrait")));
        assertTrue(loaded.problems().stream().anyMatch(problem -> problem.contains("both next and choices")));
        assertTrue(loaded.problems().contains("Missing target: missing"));
        assertTrue(loaded.problems().contains("Missing target: other"));
    }
}
