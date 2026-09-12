package me.blockcraft.duell.game;

import java.util.Arrays;
import java.util.Optional;

public enum DuelMode {
    VS("1vs1", "vs"),
    SG("SG", "sg"),
    SUPERJUMP("SJ", "superjump");

    private final String setupKey;
    private final String configKey;

    DuelMode(String setupKey, String configKey) {
        this.setupKey = setupKey;
        this.configKey = configKey;
    }

    /** The mode label used by {@code /setspawn} and the location file. */
    public String setupKey() {
        return setupKey;
    }

    public String configKey() {
        return configKey;
    }

    public static Optional<DuelMode> bySetupKey(String key) {
        return Arrays.stream(values()).filter(mode -> mode.setupKey.equalsIgnoreCase(key)).findFirst();
    }
}
