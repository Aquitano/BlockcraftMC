package me.blockcraft.lobby.config;

import me.blockcraft.api.pack.MenuIcon;

import java.util.Arrays;
import java.util.Optional;

public enum LobbyLocation {
    SPAWN("spawn"),
    DUELL("duell"),
    QSG("qsg"),
    JUMP("jump"),
    RUSH("rush"),
    BEDWARS("bedwars"),
    MLGRUSH("mlgrush"),
    ZOMBIES("zombies"),
    CORES("cores"),
    ENDERGAMES("endergames"),
    TTT("ttt"),
    SKYWARS("skywars"),
    SHOP("shop"),
    PREMIUM("premium"),
    SHOP_NPC("shop-npc");

    private final String key;

    LobbyLocation(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The game whose area this is, by its icon; the spawn, the jump and the shops are none. */
    public Optional<MenuIcon> game() {
        return Optional.ofNullable(switch (this) {
            case QSG -> MenuIcon.QUICKSG;
            case RUSH -> MenuIcon.EMERALDRUSH;
            case DUELL -> MenuIcon.DUELL;
            case BEDWARS -> MenuIcon.BEDWARS;
            case MLGRUSH -> MenuIcon.MLGRUSH;
            case ZOMBIES -> MenuIcon.ZOMBIES;
            case CORES -> MenuIcon.CORES;
            case ENDERGAMES -> MenuIcon.ENDERGAMES;
            case TTT -> MenuIcon.TTT;
            case SKYWARS -> MenuIcon.SKYWARS;
            case SPAWN, JUMP, SHOP, PREMIUM, SHOP_NPC -> null;
        });
    }

    public static Optional<LobbyLocation> byKey(String key) {
        return Arrays.stream(values()).filter(location -> location.key.equals(key)).findFirst();
    }
}
