package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FighterAimTest {

    private static Fighter fighter() {
        return new Fighter(new Heat.Rules(10, 5, 40, 0.2, 0.25), ability -> new Charges(1, 20));
    }

    @Test
    public void theAimAHoldBeganWithLastsWhileNoUseCanCome() {
        var fighter = fighter();
        fighter.aimed(10);

        hold(fighter, 11, 40);

        assertTrue(fighter.aiming(40));
    }

    @Test
    public void anAskedClientKeepsItsAimOnlyByUsingAgain() {
        var fighter = fighter();
        fighter.aimed(10);
        hold(fighter, 11, 20);
        assertTrue(fighter.askAim(20));
        fighter.stoppedBreaking(21);

        hold(fighter, 21, 30);
        assertFalse(fighter.aiming(30));

        fighter.aimed(31);
        hold(fighter, 32, 45);
        assertTrue(fighter.aiming(45));
    }

    @Test
    public void aStopNobodyAskedForLeavesTheAimAlone() {
        var fighter = fighter();
        fighter.aimed(10);
        fighter.stoppedBreaking(12);

        hold(fighter, 11, 30);

        assertTrue(fighter.aiming(30));
    }

    private static void hold(Fighter fighter, long from, long to) {
        for (long tick = from; tick <= to; tick++) {
            fighter.holdAim(tick);
        }
    }
}
