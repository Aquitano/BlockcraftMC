package me.blockcraft.sternenfront.secret;

import java.util.Arrays;
import java.util.List;

/** What a player can find on Tessar, each one row in bc_unlocks. The crystals come in the map's finding order. */
enum Secret {
    KYBER_WRECK("kyber.1", "kyber-1"),
    KYBER_TEMPLE("kyber.2", "kyber-2"),
    KYBER_CANTINA("kyber.3", "kyber-3"),
    KYBER_REFINERY("kyber.4", "kyber-4"),
    KYBER_LEDGE("kyber.5", "kyber-5"),
    BRUMMI("brummi", "brummi"),
    SAND_BEAST("sand_beast", "sand-beast"),
    CANTINA("cantina", "cantina"),
    KESSEL("kessel", "kessel"),
    RECEIVER("receiver", "receiver"),
    INSPECTION("inspection", "inspection");

    static final String PREFIX = "sternenfront.";
    static final String KYBER = PREFIX + "kyber.";
    static final List<Secret> CRYSTALS = Arrays.stream(values()).filter(Secret::crystal).toList();

    private final String key;
    private final String name;

    Secret(String key, String name) {
        this.key = key;
        this.name = name;
    }

    String item() {
        return PREFIX + key;
    }

    String messageKey() {
        return "secret.name." + name;
    }

    boolean crystal() {
        return key.startsWith("kyber.");
    }
}
