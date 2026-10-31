package me.blockcraft.api.menu;

import me.blockcraft.api.achievement.Achievement;
import me.blockcraft.api.achievement.Metric;
import me.blockcraft.api.cosmetic.CosmeticReward;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.task.LatestRequests;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProgressionMenuTest {

    @Test
    public void onlyTheCosmeticUnlockMakesAMilestoneOwned() {
        var confetti = milestone(3, "effect.win.confetti");
        var achievementOnly = Set.of(confetti.item());

        assertEquals(ProgressionMenu.State.PENDING,
                ProgressionMenu.state(confetti, 3, achievementOnly, Optional.of(confetti)));
        assertEquals(ProgressionMenu.State.OWNED,
                ProgressionMenu.state(confetti, 1, Set.of("effect.win.confetti"), Optional.empty()));
    }

    @Test
    public void theFirstMissingCosmeticIsNextAndLaterMilestonesStayLocked() {
        var confetti = milestone(3, "effect.win.confetti");
        var tag = milestone(5, "tag.aufsteiger");
        var stardust = milestone(15, "effect.kill.stardust");
        var rewards = List.of(confetti, tag, stardust);
        var owned = Set.of("effect.win.confetti");
        var next = ProgressionMenu.nextReward(rewards, owned);

        assertEquals(Optional.of(tag), next);
        assertEquals(ProgressionMenu.State.NEXT, ProgressionMenu.state(tag, 4, owned, next));
        assertEquals(ProgressionMenu.State.LOCKED, ProgressionMenu.state(stardust, 4, owned, next));
    }

    @Test
    public void anOwnedFinalRewardLeavesNoNextMilestone() {
        var aurora = milestone(40, "effect.win.aurora");

        assertTrue(ProgressionMenu.nextReward(List.of(aurora), Set.of("effect.win.aurora")).isEmpty());
        assertTrue(ProgressionMenu.nextReward(List.of(), Set.of()).isEmpty());
    }

    @Test
    public void aNewerSelectionCancelsOnlyItsSettingAndNavigationCancelsBoth() {
        var selections = new LatestRequests<PlayerSetting>();
        var active = new AtomicBoolean(true);
        var oldWin = ProgressionMenu.beginSelection(selections, PlayerSetting.WIN_EFFECT, active::get);
        var kill = ProgressionMenu.beginSelection(selections, PlayerSetting.KILL_EFFECT, active::get);
        var newWin = ProgressionMenu.beginSelection(selections, PlayerSetting.WIN_EFFECT, active::get);

        assertFalse(oldWin.getAsBoolean());
        assertTrue(kill.getAsBoolean());
        assertTrue(newWin.getAsBoolean());

        active.set(false);
        assertFalse(kill.getAsBoolean());
        assertFalse(newWin.getAsBoolean());
    }

    private static Achievement milestone(int level, String item) {
        return new Achievement(Achievement.NETWORK, "level-" + level, Metric.LEVEL, level, 0, CosmeticReward.parse(item));
    }
}
