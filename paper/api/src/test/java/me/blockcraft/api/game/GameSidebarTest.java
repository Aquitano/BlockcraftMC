package me.blockcraft.api.game;

import net.kyori.adventure.text.Component;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GameSidebarTest {
    @Test
    public void shortBoardsKeepTheirSectionSeparators() {
        var first = new GameSidebar.Section(Component.text("Players"), Component.text("2/2"));
        var second = new GameSidebar.Section(Component.text("Mode"), Component.text("MLG Rush"));
        assertEquals(List.of(first.heading(), first.values().getFirst(), Component.empty(), second.heading(),
                second.values().getFirst()), GameSidebar.layout(List.of(first, second)));
    }

    @Test
    public void tourScoreSurvivesSpectatorsAndASidebarClockByUsingOnlyTheAvailableGaps() {
        for (boolean spectators : List.of(false, true)) {
            for (boolean clock : List.of(false, true)) {
                var sections = new ArrayList<GameSidebar.Section>();
                sections.add(new GameSidebar.Section(Component.text("Players"), spectators
                        ? List.of(Component.text("2/2"), Component.text("Spectators: 1")) : List.of(Component.text("2/2"))));
                if (clock) {
                    sections.add(new GameSidebar.Section(Component.text("Time"), Component.text("1:00")));
                }
                sections.add(new GameSidebar.Section(Component.text("Mode"), Component.text("MLG Rush, unrated")));
                sections.add(new GameSidebar.Section(Component.text("Opponent"), Component.text("Thomas")));
                sections.add(new GameSidebar.Section(Component.text("Points"), Component.text("3")));
                var score = Component.text("2:0");
                sections.add(new GameSidebar.Section(Component.text("Tour"), List.of(Component.text("3/3"), score)));

                var rows = GameSidebar.layout(sections);
                assertTrue(rows.size() <= 15);
                assertEquals(score, rows.getLast());
                assertEquals(sections.stream().flatMap(section -> java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(section.heading()), section.values().stream())).toList(),
                        rows.stream().filter(row -> !row.equals(Component.empty())).toList());
                if (clock || spectators) {
                    assertEquals(15, rows.size());
                }
            }
        }
    }
}
