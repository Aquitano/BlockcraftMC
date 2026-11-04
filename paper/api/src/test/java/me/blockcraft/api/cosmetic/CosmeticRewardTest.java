package me.blockcraft.api.cosmetic;

import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Material;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CosmeticRewardTest {

    @Test
    public void earnedRewardsKeepTheirUnlockAndSelectionKeys() {
        var confetti = CosmeticReward.parse("effect.win.confetti").orElseThrow();
        assertEquals("effect.win.confetti", confetti.item());
        assertEquals(PlayerSetting.WIN_EFFECT, confetti.setting());
        assertEquals("confetti", confetti.value());
        assertEquals(WinEffect.CONFETTI.icon(), confetti.icon());
        assertEquals(Optional.of(WinEffect.CONFETTI), WinEffect.byKey(confetti.value()));

        var stardust = CosmeticReward.parse("effect.kill.stardust").orElseThrow();
        assertEquals("effect.kill.stardust", stardust.item());
        assertEquals(PlayerSetting.KILL_EFFECT, stardust.setting());
        assertEquals("stardust", stardust.value());
        assertEquals(KillEffect.STARDUST.icon(), stardust.icon());
        assertEquals(Optional.of(KillEffect.STARDUST), KillEffect.byKey(stardust.value()));

        var aurora = CosmeticReward.parse("effect.win.aurora").orElseThrow();
        assertEquals("effect.win.aurora", aurora.item());
        assertEquals(PlayerSetting.WIN_EFFECT, aurora.setting());
        assertEquals("aurora", aurora.value());
        assertEquals(WinEffect.AURORA.icon(), aurora.icon());
        assertEquals(Optional.of(WinEffect.AURORA), WinEffect.byKey(aurora.value()));

        var tag = CosmeticReward.parse("tag.aufsteiger").orElseThrow();
        assertEquals("tag.aufsteiger", tag.item());
        assertEquals(PlayerSetting.CHAT_TAG, tag.setting());
        assertEquals("aufsteiger", tag.value());
        assertEquals(Material.NAME_TAG, tag.icon());
    }

    @Test
    public void purchasedEffectsRemainSeparateFromEarnedRewards() {
        assertEquals(List.of("blood", "lightning", "explosion", "flames", "hearts", "soul"),
                Arrays.stream(KillEffect.values()).filter(effect -> !effect.earned()).map(KillEffect::key).toList());
        assertEquals(List.of("fireworks", "storm", "totem", "dragon", "notes"),
                Arrays.stream(WinEffect.values()).filter(effect -> !effect.earned()).map(WinEffect::key).toList());
        assertEquals(List.of("stardust"),
                Arrays.stream(KillEffect.values()).filter(KillEffect::earned).map(KillEffect::key).toList());
        assertEquals(List.of("confetti", "aurora"),
                Arrays.stream(WinEffect.values()).filter(WinEffect::earned).map(WinEffect::key).toList());
        assertThrows(IllegalArgumentException.class, () -> new CosmeticReward.Kill(KillEffect.BLOOD));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticReward.Win(WinEffect.FIREWORKS));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticReward.Tag(ChatTagChoice.of(ChatTag.PRO)));
    }
}
