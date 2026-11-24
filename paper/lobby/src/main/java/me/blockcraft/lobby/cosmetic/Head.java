package me.blockcraft.lobby.cosmetic;

import java.util.Arrays;
import java.util.Optional;

public enum Head {
    CAPTAIN_AMERICA("capam", "MURICA"),
    GRUMPY_CAT("crump", "GrumpyCat"),
    HAMBURGER("hamburger", "a", "Sloggy_Whopper"),
    CHEST("chest", "ab", "Zealock"),
    PISTON("piston", "abs", "SkillOuTes"),
    COMMAND_BLOCK("command-block", "abss", "ExtrayeaMC"),
    DISPENSER("dispenser", "dis", "TreePuncher105"),
    RUBBER("rubber", "diss", "Trajan"),
    TNT("tnt", "disss", "Eternal"),
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
    private final String storageKey;

    Head(String key, String owner) {
        this(key, key, owner);
    }

    Head(String key, String storageKey, String owner) {
        this.key = key;
        this.storageKey = storageKey;
        this.owner = owner;
    }

    public static Optional<Head> byKey(String key) {
        return Arrays.stream(values()).filter(head -> (head.key.equals(key) || head.storageKey.equals(key))).findFirst();
    }

    public String key() {
        return key;
    }

    public String item() {
        return ITEM_PREFIX + storageKey;
    }

    public String storageKey() {
        return storageKey;
    }

    public String owner() {
        return owner;
    }
}
