package me.blockcraft.knockbackffa;

import org.junit.Test;

import java.util.EnumSet;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class FfaKitTest {

    @Test
    public void everyChangeHandsOutAnotherKitAndEveryKitComesUp() {
        var random = new SplittableRandom(7);
        for (var current : FfaKit.values()) {
            var seen = EnumSet.noneOf(FfaKit.class);
            for (int i = 0; i < 500; i++) {
                var next = current.next(random);
                assertNotEquals(current, next);
                seen.add(next);
            }
            assertEquals(FfaKit.values().length - 1, seen.size());
        }
    }
}
