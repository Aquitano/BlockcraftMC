package me.blockcraft.common.settings;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerSettingTest {

    @Test
    public void aPlayerWithoutARowKeepsTheSettingsOwnDefault() {
        assertTrue(PlayerSetting.PRIVATE_MESSAGES.enabled(Optional.empty()));
        assertFalse(PlayerSetting.AUTO_NICK.enabled(Optional.empty()));
        assertFalse(PlayerSetting.KEEP_NICK.enabled(Optional.empty()));
    }

    @Test
    public void aStoredValueWinsOverTheDefault() {
        assertFalse(PlayerSetting.PRIVATE_MESSAGES.enabled(Optional.of(PlayerSetting.OFF)));
        assertTrue(PlayerSetting.AUTO_NICK.enabled(Optional.of(PlayerSetting.ON)));
    }

    @Test
    public void theNickTogglesAreOnlyForPlayersWhoMayNick() {
        assertEquals(Optional.of("blockcraft.nick"), PlayerSetting.AUTO_NICK.permission());
        assertEquals(Optional.empty(), PlayerSetting.SOUNDS.permission());
    }
}
