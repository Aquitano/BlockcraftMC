package me.blockcraft.quicksg.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum LocationKey {
    LOBBY("Lobby"),
    SPECTATOR("Spectator"),
    POS1("Pos1"),
    POS2("Pos2"),
    POS3("Pos3"),
    POS4("Pos4"),
    POS5("Pos5"),
    POS6("Pos6"),
    POS7("Pos7"),
    POS8("Pos8"),
    DM1("DM1"),
    DM2("DM2"),
    DM3("DM3"),
    DM4("DM4"),
    LOBBY_CENTER("Lobbymitte"),
    GAME_CENTER("InGamemitte");

    private static final List<LocationKey> SPAWNS = List.of(POS1, POS2, POS3, POS4, POS5, POS6, POS7, POS8);

    private final String key;

    LocationKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static List<LocationKey> spawns() {
        return SPAWNS;
    }

    public static Optional<LocationKey> parse(String input) {
        return Arrays.stream(values()).filter(value -> value.key.equalsIgnoreCase(input)).findFirst();
    }
}
