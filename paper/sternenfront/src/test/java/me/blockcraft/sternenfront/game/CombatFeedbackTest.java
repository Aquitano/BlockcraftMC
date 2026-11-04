package me.blockcraft.sternenfront.game;

import net.kyori.adventure.text.Component;
import org.junit.Test;

import static org.junit.Assert.*;

public class CombatFeedbackTest {

    @Test
    public void killAndHeadshotFeedbackSurviveWeakerHitsAndBurstSoundsDoNotStack() {
        var state = new CombatFeedback.State();
        assertTrue(state.hit(CombatFeedback.Marker.HEADSHOT, 0, 6));
        assertFalse(state.hit(CombatFeedback.Marker.HIT, 0, 6));
        assertEquals(CombatFeedback.Marker.HEADSHOT, state.marker);
        assertTrue(state.hit(CombatFeedback.Marker.KILL, 0, 6));
        state.hit(CombatFeedback.Marker.HIT, 1, 6);
        assertEquals(CombatFeedback.Marker.KILL, state.marker);
        state.hit(CombatFeedback.Marker.HIT, 6, 6);
        assertEquals(CombatFeedback.Marker.HIT, state.marker);
    }

    @Test
    public void nearbyPointGainsCombineWithoutReplacingAKillWithASupportLabel() {
        var state = new CombatFeedback.State();
        var target = Component.text("Droid");
        state.earned(CombatFeedback.Reward.KILL, target, 100, 0, 30);
        state.earned(CombatFeedback.Reward.DEFENCE, Component.empty(), 25, 1, 30);
        assertEquals(125, state.points);
        assertEquals(CombatFeedback.Reward.KILL, state.reward);
        assertEquals(target, state.target);
        state.earned(CombatFeedback.Reward.KILL, target, 25, 2, 30);
        assertEquals(2, state.kills);
        assertEquals(150, state.points);
        state.earned(CombatFeedback.Reward.ASSIST, Component.empty(), 40, 32, 30);
        assertEquals(CombatFeedback.Reward.ASSIST, state.reward);
        assertEquals(40, state.points);
        assertEquals(0, state.kills);
    }
}
