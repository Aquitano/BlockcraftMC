package me.blockcraft.api.notification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RewardBatchTest {
    @Test
    public void rapidXpAwardsCombineWithoutExtendingTheWindow() {
        var batch = new RewardBatch();
        batch.add(10, 0, 100, 10);
        batch.add(25, 4, 105, 10);
        batch.add(50, 5, 109, 10);
        assertNull(batch.take(109));
        assertEquals(new RewardBatch.Xp(85, 5), batch.take(110));
        assertTrue(batch.isEmpty());
        batch.add(3, 0, 111, 10);
        assertEquals(new RewardBatch.Xp(3, 0), batch.take(121));
    }
}
