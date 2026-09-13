package me.blockcraft.common.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The per-player choices the network remembers, read on both platforms. A cosmetic setting stores the
 * {@code key()} of the chosen enum constant; a toggle stores {@link #ON} or {@link #OFF} and counts as on until
 * a row says otherwise, so a player who never opened the settings menu is reachable, hears sounds and sees others.
 */
public enum PlayerSetting {

    CHAT_TAG("chat-tag", false),
    KILL_EFFECT("kill-effect", false),
    WIN_EFFECT("win-effect", false),
    PRIVATE_MESSAGES("private-messages", true),
    PARTY_INVITES("party-invites", true),
    FRIEND_REQUESTS("friend-requests", true),
    PLAYER_VISIBILITY("player-visibility", true),
    SOUNDS("sounds", true);

    public static final String ON = "on";
    public static final String OFF = "off";

    private final String key;
    private final boolean toggle;

    PlayerSetting(String key, boolean toggle) {
        this.key = key;
        this.toggle = toggle;
    }

    /** The on/off settings, in the order the settings menu lays them out. */
    public static List<PlayerSetting> toggles() {
        return Arrays.stream(values()).filter(PlayerSetting::isToggle).toList();
    }

    public static Optional<PlayerSetting> byKey(String key) {
        return Arrays.stream(values()).filter(setting -> setting.key.equals(key)).findFirst();
    }

    public static boolean enabled(Optional<String> stored) {
        return stored.filter(OFF::equals).isEmpty();
    }

    public static String value(boolean enabled) {
        return enabled ? ON : OFF;
    }

    public String key() {
        return key;
    }

    public boolean isToggle() {
        return toggle;
    }
}
