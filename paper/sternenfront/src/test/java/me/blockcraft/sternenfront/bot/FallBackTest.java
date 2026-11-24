package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.bot.FallBack.Order;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FallBackTest {

    @Test
    public void anOutnumberedTeamPushesAgainOnceBackWithTheOddsEven() {
        var fallBack = new FallBack();
        assertEquals(Order.FALL_BACK, fallBack.update(true, false, true, false, 0, 160));
        assertEquals(Order.FALL_BACK, fallBack.update(false, false, true, false, 20, 160));
        assertEquals(Order.PUSH, fallBack.update(false, false, false, true, 40, 160));
    }

    @Test
    public void oddsThatStayBadOrTurnBadAgainMakeItGiveTheObjectiveUp() {
        var stayed = new FallBack();
        assertEquals(Order.FALL_BACK, stayed.update(true, false, true, false, 0, 160));
        assertEquals(Order.FALL_BACK, stayed.update(true, false, true, true, 140, 160));
        assertEquals(Order.GIVE_UP, stayed.update(true, false, true, true, 160, 160));
        var returned = new FallBack();
        returned.update(true, false, true, false, 0, 160);
        assertEquals(Order.PUSH, returned.update(false, false, true, true, 60, 160));
        assertEquals(Order.GIVE_UP, returned.update(true, false, true, false, 120, 160));
    }

    @Test
    public void aLeaderLeftAloneWaitsOnceForAtMostTheFallBackTime() {
        var fallBack = new FallBack();
        assertEquals(Order.PUSH, fallBack.update(false, true, false, false, 0, 160));
        assertEquals(Order.FALL_BACK, fallBack.update(false, true, true, false, 20, 160));
        assertEquals(Order.FALL_BACK, fallBack.update(false, true, false, true, 100, 160));
        assertEquals(Order.PUSH, fallBack.update(false, true, true, true, 180, 160));
        assertEquals(Order.PUSH, fallBack.update(false, true, true, true, 200, 160));
        assertEquals(Order.PUSH, fallBack.update(false, false, true, true, 220, 160));
        assertEquals(Order.FALL_BACK, fallBack.update(false, true, true, false, 240, 160));
    }
}
