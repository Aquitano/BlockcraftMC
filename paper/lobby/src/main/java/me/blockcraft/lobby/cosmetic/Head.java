package me.blockcraft.lobby.cosmetic;

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

    private final String key;
    private final String owner;

    Head(String key, String owner) {
        this.key = key;
        this.owner = owner;
    }

    public String key() {
        return key;
    }

    public String owner() {
        return owner;
    }
}
