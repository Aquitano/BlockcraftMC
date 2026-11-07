package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.combat.Stance;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HeroStancesTest {

    @Test
    public void theGuardBringsBothHandsInAndCrossesTheBladeTowardTheOffHand() {
        var guard = HeroStances.of(Optional.of(Stance.Still.GUARD), -.5f);
        assertTrue(guard.rightTurn() > 0 && guard.leftTurn() < 0);
        assertTrue(guard.bladeRoll() < 0 && guard.bladeX() > 0);
        assertTrue(guard.rightLift() < -.5f && guard.leftLift() < -.5f);
    }

    @Test
    public void aSwingWindsUpWhereItStartsAndFollowsThroughOverTheRestOfTheSweep() {
        var windUp = HeroStances.of(Optional.of(new Stance.Swing(false, 0, 4)), -.5f);
        var through = HeroStances.of(Optional.of(new Stance.Swing(false, 2, 4)), -.5f);
        assertTrue(windUp.bladeTurn() < 0 && through.bladeTurn() > 0);
        assertEquals(1, windUp.ease());
        assertEquals(3, through.ease());
        assertTrue(HeroStances.of(Optional.of(new Stance.Swing(true, 0, 4)), -.5f).bladeTurn() > 0);
    }

    @Test
    public void aFighterWithoutAStanceKeepsItsBodysPose() {
        var rest = HeroStances.of(Optional.empty(), -1.1f);
        assertEquals(new HeroStances.Limbs(-1.1f, 0, -1.1f, 0, 0, 0, 0, 0, 0, 1), rest);
    }
}
