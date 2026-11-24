package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TriggerTest {

    @Test
    public void aClickOnATriggerCellIsOnePullAndHeldWhileItSwings() {
        var trigger = new Trigger();

        assertTrue(trigger.started(10));
        assertFalse(trigger.swung(10));
        assertFalse(trigger.swung(11));
        assertTrue(trigger.held(12));

        trigger.stopped(12);
        assertFalse(trigger.held(12));
    }

    @Test
    public void aRestartWithinTheHoldIsNoNewPull() {
        var trigger = new Trigger();
        trigger.started(10);
        trigger.swung(10);

        trigger.stopped(14);
        assertFalse(trigger.started(15));

        assertTrue(trigger.held(15));
    }

    @Test
    public void aClickAfterTheReleaseIsANewPull() {
        var trigger = new Trigger();
        trigger.started(10);
        trigger.stopped(12);
        trigger.tick(13);
        trigger.tick(14);

        assertTrue(trigger.started(14));
    }

    @Test
    public void aClickThatMeetsNoCellIsAPullAndItsReleaseAtOnce() {
        var trigger = new Trigger();

        assertTrue(trigger.swung(10));
        assertFalse(trigger.held(10));
        assertTrue(trigger.swung(13));
    }

    @Test
    public void theCellThatAppearsUnderAClickCarriesThatClickOn() {
        var trigger = new Trigger();
        assertTrue(trigger.swung(10));

        assertFalse(trigger.started(11));

        assertTrue(trigger.held(11));
    }

    @Test
    public void swingsOnEndWithNoStartAreAHoldThatEndsWithThem() {
        var trigger = new Trigger();
        assertTrue(trigger.swung(10));
        assertFalse(trigger.unstarted(10));

        assertTrue(trigger.swung(11));

        assertTrue(trigger.held(11));
        assertTrue(trigger.unstarted(11));
        assertFalse(trigger.unstarted(11));
        assertTrue(trigger.held(12));
        assertFalse(trigger.held(13));
    }

    @Test
    public void theStartAHoldWasAskedForIsNoNewPull() {
        var trigger = new Trigger();
        trigger.swung(10);
        trigger.swung(11);

        assertFalse(trigger.started(12));

        assertFalse(trigger.swung(12));
        assertTrue(trigger.held(17));
    }

    @Test
    public void aSwingRightAfterAnAbortCarriesTheHoldOn() {
        var trigger = new Trigger();
        trigger.started(10);
        trigger.swung(10);
        trigger.stopped(10);

        assertFalse(trigger.swung(11));

        assertTrue(trigger.held(11));
        assertFalse(trigger.held(13));
    }

    @Test
    public void aTriggerThatStopsSwingingIsNoLongerHeld() {
        var trigger = new Trigger();
        trigger.started(10);
        trigger.swung(10);

        assertTrue(trigger.held(15));
        assertFalse(trigger.held(16));
        assertFalse(trigger.swung(17));
        assertTrue(trigger.held(17));
    }
}
