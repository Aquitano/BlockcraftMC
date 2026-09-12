package me.blockcraft.lobby.chat;

public final class ChatMute {

    private boolean muted;

    public boolean isMuted() {
        return muted;
    }

    /** Flips the mute state and returns the new one. */
    public boolean toggle() {
        muted = !muted;
        return muted;
    }
}
