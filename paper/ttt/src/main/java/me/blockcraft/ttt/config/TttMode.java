package me.blockcraft.ttt.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Which TTT a server plays, set by {@code BLOCKCRAFT_TTT_MODE} beside the server's group: classic TTT, or TTT 2 with
 * chores and sabotage ({@code tasks}). Both read the same config.yml and messages.yml.
 */
public enum TttMode {
    CLASSIC,
    TASKS;

    private static final String ENVIRONMENT = "BLOCKCRAFT_TTT_MODE";

    public static TttMode fromEnvironment() {
        return Optional.ofNullable(System.getenv(ENVIRONMENT))
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .filter("tasks"::equals)
                .map(value -> TASKS)
                .orElse(CLASSIC);
    }
}
