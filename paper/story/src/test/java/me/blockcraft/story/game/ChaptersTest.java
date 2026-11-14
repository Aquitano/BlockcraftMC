package me.blockcraft.story.game;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ChaptersTest {

    private static final List<String> STAGES = List.of("briefing", "signal", "ending", "debrief");

    private static Chapters chapters(String reliefFrom) {
        var config = new YamlConfiguration();
        config.set("signal.from", "briefing");
        config.set("relief.from", reliefFrom);
        config.set("relief.carries", List.of("decision"));
        return Chapters.load("example", STAGES, config);
    }

    @Test
    public void aChapterRunsFromItsFirstStageUpToTheNextChaptersUnderItsOwnKey() {
        var chapters = chapters("debrief");
        assertEquals(List.of("briefing", "signal", "ending"), chapters.named("signal").orElseThrow().stages());
        assertEquals("example.relief", chapters.of("debrief").orElseThrow().campaignKey());
        assertEquals(List.of("decision"), chapters.named("relief").orElseThrow().carries());
        assertEquals(chapters.named("signal"), chapters.before(chapters.named("relief").orElseThrow()));
    }

    @Test
    public void aPartyPlaysTheEarliestChapterAMemberHasStillToFinishAndStartsOverAfterTheLast() {
        var chapters = chapters("debrief");
        assertEquals("signal", chapters.next(List.of(Set.of())).name());
        assertEquals("relief", chapters.next(List.of(Set.of("signal"))).name());
        assertEquals("signal", chapters.next(List.of(Set.of("signal", "relief"), Set.of())).name());
        assertEquals("signal", chapters.next(List.of(Set.of("signal", "relief"))).name());
        assertEquals("signal", chapters.next(List.of(Set.of("relief"))).name());
    }

    @Test
    public void aMissionWithoutChaptersIsOneChapterUnderItsOwnName() {
        var whole = Chapters.load("example", STAGES, null).all();
        assertEquals(1, whole.size());
        assertEquals("example", whole.getFirst().campaignKey());
        assertEquals(STAGES, whole.getFirst().stages());
    }

    @Test
    public void aChapterThatStartsAtNoStageOrOutOfOrderIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> chapters("nowhere"));
        assertThrows(IllegalArgumentException.class, () -> chapters("briefing"));
    }
}
