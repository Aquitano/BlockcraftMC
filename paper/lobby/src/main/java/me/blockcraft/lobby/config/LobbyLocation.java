package me.blockcraft.lobby.config;

public enum LobbyLocation {
    SPAWN("spawn"),
    DUELL("duell"),
    QSG("qsg"),
    JUMP("jump"),
    RUSH("rush"),
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
}
