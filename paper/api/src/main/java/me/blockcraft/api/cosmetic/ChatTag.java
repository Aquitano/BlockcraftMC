package me.blockcraft.api.cosmetic;

import java.util.Arrays;
import java.util.Optional;

/** Tags a premium player can show in front of their name; the text lives in BlockcraftAPI's {@code tags.<key>}. */
public enum ChatTag {
    PRO("pro"),
    LEGEND("legend"),
    GG("gg"),
    OG("og"),
    KING("king"),
    NOOB("noob"),
    SWEAT("sweat"),
    CHILL("chill");

    private final String key;

    ChatTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String messageKey() {
        return "tags." + key;
    }

    public static Optional<ChatTag> byKey(String key) {
        return Arrays.stream(values()).filter(tag -> tag.key.equals(key)).findFirst();
    }
}
