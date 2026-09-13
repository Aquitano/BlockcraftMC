package me.blockcraft.api.settings;

/** The per-player choices the network remembers; values are the {@code key()} of the chosen enum constant. */
public enum PlayerSetting {
    CHAT_TAG("chat-tag"),
    KILL_EFFECT("kill-effect"),
    WIN_EFFECT("win-effect");

    private final String key;

    PlayerSetting(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
