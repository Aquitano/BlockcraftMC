package me.blockcraft.lobby.cosmetic;

import java.util.Arrays;
import java.util.Optional;

/** Hidden signs in the lobby, in the order they appear in the secrets menu. */
public enum Secret {
    ZIMMER("Zimmer"),
    WAGEN("Wagen"),
    NACHT("Nacht"),
    ABWASSER("Abwasser"),
    ABFALL("Abfall"),
    HINRICHTUNG("Hinrichtung"),
    STATUE("Statue"),
    RUHE("Ruhe"),
    ABGELEGEN("Abgelegen");

    public static final String ITEM_PREFIX = "lobby.secret.";

    private final String key;

    Secret(String key) {
        this.key = key;
    }

    public static Optional<Secret> byKey(String key) {
        return Arrays.stream(values()).filter(secret -> secret.key.equalsIgnoreCase(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public String item() {
        return ITEM_PREFIX + key.toLowerCase();
    }

    public String messageKey() {
        return "secrets.name." + key.toLowerCase();
    }
}
