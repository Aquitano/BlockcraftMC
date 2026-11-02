package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandPostTest {

    @Test
    public void oneAttackerTakesANeutralPostInTheCaptureTime() {
        var post = new CommandPost(10);

        for (int second = 0; second < 9; second++) {
            assertEquals(Optional.empty(), post.update(1, 0, 1));
        }

        assertEquals(Optional.of(new CommandPost.Change.Captured(Faction.KOLONIEN)), post.update(1, 0, 1));
        assertEquals(Optional.of(Faction.KOLONIEN), post.owner());
    }

    @Test
    public void anEnemyPostIsNeutralisedBeforeItCanBeTakenAndALeadOfThreeIsThreeTimesAsFast() {
        var post = new CommandPost(9);
        for (int second = 0; second < 3; second++) {
            post.update(0, 3, 1);
        }
        assertEquals(Optional.of(Faction.HEGEMONIE), post.owner());

        post.update(4, 0, 1);
        post.update(4, 0, 1);

        assertEquals(Optional.of(new CommandPost.Change.Neutralised(Faction.HEGEMONIE)), post.update(4, 0, 1));
        assertEquals(Optional.empty(), post.owner());
        assertEquals(0, post.share(Faction.KOLONIEN), 0);
    }

    @Test
    public void equalNumbersHoldTheProgressAndAnEmptyZoneDriftsBack() {
        var post = new CommandPost(10);
        post.update(1, 0, 4);

        post.update(2, 2, 5);
        assertEquals(0.4, post.share(Faction.KOLONIEN), 1e-9);
        assertTrue(CommandPost.contested(2, 2));

        post.update(0, 0, 4);
        assertEquals(0.2, post.share(Faction.KOLONIEN), 1e-9);
    }
}
