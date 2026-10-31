package me.blockcraft.lobby.daily;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class DailyRewardsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final DailyRewards REWARDS = new DailyRewards(List.of(10, 15, 20), 3, "");

    @Test
    public void consecutiveDaysRaiseTheStreakAndAMissedDayStartsItAgain() {
        assertEquals(new DailyRewards.Today(1, 10, false), REWARDS.today(Optional.empty(), TODAY));
        assertEquals(new DailyRewards.Today(2, 15, false),
                REWARDS.today(Optional.of(new DailyRewards.Claimed(1, TODAY.minusDays(1))), TODAY));
        assertEquals(new DailyRewards.Today(1, 10, false),
                REWARDS.today(Optional.of(new DailyRewards.Claimed(5, TODAY.minusDays(2))), TODAY));
    }

    @Test
    public void aClaimTodayCannotBeRepeated() {
        assertEquals(new DailyRewards.Today(2, 15, true),
                REWARDS.today(Optional.of(new DailyRewards.Claimed(2, TODAY)), TODAY));
        assertTrue(REWARDS.today(Optional.of(new DailyRewards.Claimed(2, TODAY.plusDays(1))), TODAY).claimed());
    }

    @Test
    public void theLastRewardRepeatsPastTheEndOfTheList() {
        assertEquals(new DailyRewards.Today(9, 20, false),
                REWARDS.today(Optional.of(new DailyRewards.Claimed(8, TODAY.minusDays(1))), TODAY));
        assertEquals(List.of(10, 15, 20, 25, 30, 40, 60), DailyRewards.fromYaml(Map.of()).coins());
    }
}
