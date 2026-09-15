package me.blockcraft.rush.config;

import java.util.List;
import java.util.Optional;

public enum SpawnPoint {
    LOBBY("lobby"),
    SPECTATOR("spectator"),
    SEARCH("search"),
    DEATHMATCH_1("dm1"),
    DEATHMATCH_2("dm2"),
    DEATHMATCH_3("dm3"),
    DEATHMATCH_4("dm4"),
    DEATHMATCH_5("dm5"),
    DEATHMATCH_6("dm6"),
    ENCHANT("enchant"),
    SHOPPING("shopping");

    public static final List<SpawnPoint> DEATHMATCH =
            List.of(DEATHMATCH_1, DEATHMATCH_2, DEATHMATCH_3, DEATHMATCH_4, DEATHMATCH_5, DEATHMATCH_6);

    private final String key;

    SpawnPoint(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<SpawnPoint> byKey(String key) {
        for (var point : values()) {
            if (point.key.equalsIgnoreCase(key)) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }
}
