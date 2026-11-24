package me.blockcraft.bedwars.shop;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PaymentTest {

    @Test
    public void theSmallestStackPaysFirstAndTheRestStaysInTheLastSlotTouched() {
        var result = Payment.take(new int[] {0, 64, 3}, 7, 1).orElseThrow();

        assertEquals(1, result.times());
        assertArrayEquals(new int[] {0, 60, 0}, result.amounts());
    }

    @Test
    public void aShiftClickBuysAsOftenAsTheResourcesAllowUpToTheLimit() {
        var limited = Payment.take(new int[] {10, 10}, 3, 4).orElseThrow();
        var unlimited = Payment.take(new int[] {10, 10}, 3, 64).orElseThrow();

        assertEquals(4, limited.times());
        assertArrayEquals(new int[] {0, 8}, limited.amounts());
        assertEquals(6, unlimited.times());
        assertArrayEquals(new int[] {0, 2}, unlimited.amounts());
    }

    @Test
    public void tooFewResourcesBuyNothing() {
        assertTrue(Payment.take(new int[] {2, 0, 4}, 7, 1).isEmpty());
    }
}
