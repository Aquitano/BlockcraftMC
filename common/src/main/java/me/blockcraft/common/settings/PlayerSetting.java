package me.blockcraft.common.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The per-player choices the network remembers, read on both platforms. A cosmetic setting stores the
 * {@code key()} of the chosen enum constant; a toggle stores {@link #ON} or {@link #OFF} and counts as the value
 * it names here until a row says otherwise, so a player who never opened the settings menu is reachable, hears
 * sounds and sees others, while the nick toggles stay off until they are asked for. A toggle with a permission
 * belongs to the players holding it and is off for everyone else.
 */
public enum PlayerSetting {

    TRAIL("trail"),
    GADGET("gadget"),
    PET("pet"),
    HEAD("head"),
    SHIRT("shirt"),
    CHAT_TAG("chat-tag"),
    KILL_EFFECT("kill-effect"),
    WIN_EFFECT("win-effect"),
    /** The {@code /ptime} clock in ticks, kept across servers for the players who may set their own. */
    PLAYER_TIME("player-time", null, "blockcraft.core.ptime"),
    PRIVATE_MESSAGES("private-messages", PlayerSetting.ON),
    PARTY_INVITES("party-invites", PlayerSetting.ON),
    FRIEND_REQUESTS("friend-requests", PlayerSetting.ON),
    PLAYER_VISIBILITY("player-visibility", PlayerSetting.ON),
    SOUNDS("sounds", PlayerSetting.ON),
    MENTIONS("mentions", PlayerSetting.ON),
    EFFECTS("effects", PlayerSetting.ON),
    AUTO_NICK("auto-nick", PlayerSetting.OFF, "blockcraft.nick"),
    KEEP_NICK("keep-nick", PlayerSetting.OFF, "blockcraft.nick"),
    REPORT_NOTIFICATIONS("report-notifications", PlayerSetting.ON, "blockcraft.report.notify");

    public static final String ON = "on";
    public static final String OFF = "off";

    private final String key;
    private final String fallback;
    private final String permission;

    PlayerSetting(String key) {
        this(key, null, null);
    }

    PlayerSetting(String key, String fallback) {
        this(key, fallback, null);
    }

    PlayerSetting(String key, String fallback, String permission) {
        this.key = key;
        this.fallback = fallback;
        this.permission = permission;
    }

    /** The on/off settings, in the order the settings menu lays them out. */
    public static List<PlayerSetting> toggles() {
        return Arrays.stream(values()).filter(PlayerSetting::isToggle).toList();
    }

    public static Optional<PlayerSetting> byKey(String key) {
        return Arrays.stream(values()).filter(setting -> setting.key.equals(key)).findFirst();
    }

    public static String value(boolean enabled) {
        return enabled ? ON : OFF;
    }

    /** Whether the toggle is on for a player whose row holds {@code stored}, or has none. */
    public boolean enabled(Optional<String> stored) {
        return !OFF.equals(stored.orElse(fallback));
    }

    public String key() {
        return key;
    }

    public boolean isToggle() {
        return fallback != null;
    }

    /** The permission a player needs before this setting is theirs to choose. */
    public Optional<String> permission() {
        return Optional.ofNullable(permission);
    }
}
