package me.blockcraft.common.moderation;

/** Proxy-wide toggles written by the Velocity plugin and read by every Paper server. */
public enum Setting {
    GLOBAL_MUTE("globalmute"),
    MAINTENANCE("maintenance"),
    CHAT_SLOW("chatslow");

    private final String key;

    Setting(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
