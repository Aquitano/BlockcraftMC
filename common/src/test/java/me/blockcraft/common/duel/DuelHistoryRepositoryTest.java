package me.blockcraft.common.duel;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DuelHistoryRepositoryTest {

    @Test
    public void onlyTwoMutualConsecutiveDrawsQualify() {
        var one = UUID.randomUUID();
        var two = UUID.randomUUID();
        var first = new DuelHistoryRepository.Previous(two.toString(), true, null);
        var second = new DuelHistoryRepository.Previous(one.toString(), true, null);

        assertTrue(DuelHistoryRepository.consecutiveDraws(one, two, true, first, second));
        assertFalse(DuelHistoryRepository.consecutiveDraws(one, two, false, first, second));
        assertFalse(DuelHistoryRepository.consecutiveDraws(one, two, true,
                new DuelHistoryRepository.Previous(UUID.randomUUID().toString(), true, null), second));
        assertFalse(DuelHistoryRepository.consecutiveDraws(one, two, true, first,
                new DuelHistoryRepository.Previous(one.toString(), false, null)));
    }
}
