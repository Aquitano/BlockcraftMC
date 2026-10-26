package me.blockcraft.lobby.cosmetic;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/** Everything hidden in the lobby, in the order the secrets menu shows it, and how each one is found. */
public enum Secret {
    ZIMMER("Zimmer", Trigger.SIGN),
    WAGEN("Wagen", Trigger.SIGN),
    NACHT("Nacht", Trigger.SIGN),
    ABWASSER("Abwasser", Trigger.SIGN),
    ABFALL("Abfall", Trigger.SIGN),
    HINRICHTUNG("Hinrichtung", Trigger.SIGN),
    STATUE("Statue", Trigger.SIGN),
    RUHE("Ruhe", Trigger.SIGN),
    ABGELEGEN("Abgelegen", Trigger.SIGN),
    RETRO("Retro", Trigger.KONAMI_CODE),
    GOLDEN_CARP("Karpfen", Trigger.FISHING),
    GOLDEN_EGG("GoldenesEi", Trigger.CHICKEN),
    SHOOTING_STAR("Sternschnuppe", Trigger.SHOOTING_STAR),
    HEROBRINE("Herobrine", Trigger.HEROBRINE);

    /** A sign is placed in the map; the others need no map at all. The chicken is a premium pet. */
    public enum Trigger { SIGN, KONAMI_CODE, FISHING, CHICKEN, SHOOTING_STAR, HEROBRINE }

    public static final String ITEM_PREFIX = "lobby.secret.";

    private final String key;
    private final Trigger trigger;

    Secret(String key, Trigger trigger) {
        this.key = key;
        this.trigger = trigger;
    }

    public static Optional<Secret> byKey(String key) {
        return Arrays.stream(values()).filter(secret -> secret.key.equalsIgnoreCase(key)).findFirst();
    }

    /** Premium and time-limited bonus discoveries do not gate the explorer tag. */
    public static Stream<Secret> forExplorer() {
        return Arrays.stream(values()).filter(secret -> secret.trigger != Trigger.CHICKEN && secret.trigger != Trigger.SHOOTING_STAR
                && secret.trigger != Trigger.HEROBRINE);
    }

    public String key() {
        return key;
    }

    public Trigger trigger() {
        return trigger;
    }

    public String item() {
        return ITEM_PREFIX + key.toLowerCase();
    }

    public String messageKey() {
        return "secrets.name." + key.toLowerCase();
    }

    public String riddleKey() {
        return "secrets.riddle." + key.toLowerCase();
    }
}
