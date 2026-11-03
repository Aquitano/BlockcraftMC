package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ObjectivesTest {

    @Test
    public void soldiersSpreadAcrossObjectivesAndReturnToDefendContestedPosts() {
        assertEquals(1, Objectives.choose(List.of(
                new Objectives.Post(0, false, false, 20, 2),
                new Objectives.Post(1, false, false, 35, 0),
                new Objectives.Post(2, true, false, 5, 0))).orElseThrow());
        assertEquals(2, Objectives.choose(List.of(
                new Objectives.Post(0, false, false, 20, 0),
                new Objectives.Post(2, true, true, 30, 0))).orElseThrow());
    }
}
