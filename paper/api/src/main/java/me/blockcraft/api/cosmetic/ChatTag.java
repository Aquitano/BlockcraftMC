package me.blockcraft.api.cosmetic;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tags shown in front of a player's name; the text lives in BlockcraftAPI's {@code tags.<key>}. A premium player
 * picks any tag that is not earned; an earned one belongs to whoever owns it, premium or not, as a
 * {@link ChatTagChoice} unlock. A yearly tag is earned once per year and shows that year.
 */
public enum ChatTag {
    PRO("pro", false, false),
    LEGEND("legend", false, false),
    GG("gg", false, false),
    KING("king", false, false),
    NOOB("noob", false, false),
    SWEAT("sweat", false, false),
    CHILL("chill", false, false),
    OG("og", true, false),
    RETRO("1up", true, false),
    EXPLORER("entdecker", true, false),
    MIRROR("spiegel", true, false),
    EASTER_BUNNY("osterhase", true, false),
    NEW_YEAR("neujahr", true, true);

    private final String key;
    private final boolean earned;
    private final boolean yearly;

    ChatTag(String key, boolean earned, boolean yearly) {
        this.key = key;
        this.earned = earned;
        this.yearly = yearly;
    }

    public String key() {
        return key;
    }

    public boolean earned() {
        return earned;
    }

    public boolean yearly() {
        return yearly;
    }

    public String messageKey() {
        return "tags." + key;
    }

    public static Optional<ChatTag> byKey(String key) {
        return Arrays.stream(values()).filter(tag -> tag.key.equals(key)).findFirst();
    }
}
