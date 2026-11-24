package me.blockcraft.common.calendar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HourWindowTest {

    @Test
    public void aWindowEndingBeforeItStartsRunsPastMidnight() {
        var night = new HourWindow(22, 2);
        var owl = new HourWindow(3, 4);

        assertFalse(night.includes(21));
        assertTrue(night.includes(22));
        assertTrue(night.includes(1));
        assertFalse(night.includes(2));
        assertFalse(owl.includes(2));
        assertTrue(owl.includes(3));
        assertFalse(owl.includes(4));
    }
}
