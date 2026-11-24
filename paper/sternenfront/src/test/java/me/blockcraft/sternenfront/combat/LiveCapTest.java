package me.blockcraft.sternenfront.combat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveCapTest {

    @Test
    public void aFullCapPushesOutTheOldestAndNeverGrows() {
        var cap = new LiveCap<String>(2);
        assertEquals(Optional.empty(), cap.admit("first"));
        assertEquals(Optional.empty(), cap.admit("second"));
        assertFalse(cap.hasRoom());
        assertEquals(Optional.of("first"), cap.admit("third"));
        assertEquals(2, cap.size());
    }

    @Test
    public void finishedEffectsMakeRoomAndDrainEndsTheRest() {
        var cap = new LiveCap<String>(2);
        cap.admit("spent");
        cap.admit("burning");
        cap.removeIf("spent"::equals);
        assertTrue(cap.hasRoom());
        var ended = new ArrayList<String>();
        cap.drain(ended::add);
        assertEquals(List.of("burning"), ended);
        assertEquals(0, cap.size());
    }
}
