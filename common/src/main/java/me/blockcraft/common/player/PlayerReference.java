package me.blockcraft.common.player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stable identity when known; the name remains available for legacy rows and display. */
public record PlayerReference(String name, Optional<UUID> uuid) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public PlayerReference {
        if (!isName(name)) {
            throw new IllegalArgumentException("Invalid player name");
        }
        uuid = Objects.requireNonNull(uuid);
    }

    public static PlayerReference named(String name) {
        return new PlayerReference(name, Optional.empty());
    }

    public static PlayerReference known(UUID uuid, String name) {
        return new PlayerReference(name, Optional.of(uuid));
    }

    public static boolean isName(String name) {
        return name != null && NAME.matcher(name).matches();
    }
}
