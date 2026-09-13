package me.blockcraft.lobby.cosmetic;

import java.util.Arrays;
import java.util.Optional;

public enum Head {
    CAPTAIN_AMERICA("capam", "MURICA"),
    GRUMPY_CAT("crump", "GrumpyCat"),
    HAMBURGER("a", "Sloggy_Whopper"),
    CHEST("ab", "Zealock"),
    PISTON("abs", "SkillOuTes"),
    COMMAND_BLOCK("abss", "ExtrayeaMC"),
    DISPENSER("dis", "TreePuncher105"),
    RUBBER("diss", "Trajan"),
    TNT("disss", "Eternal"),
    FOOTBALL("foot", "Smaug"),
    ENDER("foots", "Edna_I"),
    MARIO("marios", "Mario"),
    YOSHI("yoshi", "Yoshi"),
    BOWSER("yoshis", "Bowser"),
    STORMTROOPER("trooper", "Stormtrooper"),
    BOBA_FETT("boba", "BobaFett"),
    MASTER_CHIEF("chief", "jscherry"),
    IRON_MAN("iron", "iron_man");

    public static final String ITEM_PREFIX = "lobby.head.";

    private final String key;
    private final String owner;

    Head(String key, String owner) {
        this.key = key;
        this.owner = owner;
    }

    public static Optional<Head> byKey(String key) {
        return Arrays.stream(values()).filter(head -> head.key.equals(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public String item() {
        return ITEM_PREFIX + key;
    }

    public String owner() {
        return owner;
    }
}
