package me.blockcraft.duell.game;

import java.util.Arrays;
import java.util.Optional;

public enum DuelMode {
    VS("1vs1", "vs"),
    SG("SG", "sg"),
    SUPERJUMP("SJ", "superjump"),
    SUMO("sumo", "sumo"),
    MLGRUSH("mlgrush", "mlgrush"),
    THROW("wurf", "throw");

    private final String setupKey;
    private final String configKey;

    DuelMode(String setupKey, String configKey) {
        this.setupKey = setupKey;
        this.configKey = configKey;
    }

    /** The mode's part of a map spawn's name in {@code /setup}. */
    public String setupKey() {
        return setupKey;
    }

    /** The mode in config.yml, the location file, the messages, the vote and {@code /forcemap}. */
    public String configKey() {
        return configKey;
    }

    /** Offered only while its season lasts, so never the fallback when nothing is playable. */
    public boolean seasonal() {
        return this == THROW;
    }

    public static Optional<DuelMode> byConfigKey(String key) {
        return Arrays.stream(values()).filter(mode -> mode.configKey.equalsIgnoreCase(key)).findFirst();
    }
}
