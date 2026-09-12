package me.blockcraft.rush.config;

import java.util.List;
import java.util.Optional;

/** Keys match the sections older config files already contain. */
public enum SpawnPoint {
    LOBBY("Lobby"),
    SPECTATOR("Spectator"),
    SEARCH("Such"),
    DEATHMATCH_1("DM1"),
    DEATHMATCH_2("DM2"),
    DEATHMATCH_3("DM3"),
    DEATHMATCH_4("DM4"),
    DEATHMATCH_5("DM5"),
    DEATHMATCH_6("DM6"),
    ENCHANT("Enchant"),
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
