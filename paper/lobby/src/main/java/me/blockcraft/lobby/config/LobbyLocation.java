package me.blockcraft.lobby.config;

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

    public static Optional<LobbyLocation> byKey(String key) {
        return Arrays.stream(values()).filter(location -> location.key.equals(key)).findFirst();
    }
}
