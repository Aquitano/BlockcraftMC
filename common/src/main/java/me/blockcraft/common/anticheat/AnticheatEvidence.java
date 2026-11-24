package me.blockcraft.common.anticheat;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AnticheatEvidence(UUID player, String name, String server, Instant recordedAt, String check,
                                String description, String violations, String verbose, Optional<Replay> replay) {

    public record Replay(UUID id, int tick) {
        public Replay {
            Objects.requireNonNull(id);
            if (tick < 0) {
                throw new IllegalArgumentException("Negative replay tick");
            }
        }
    }

    public AnticheatEvidence {
        Objects.requireNonNull(player);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(replay);
        name = text(name, 16);
        server = text(server, 64);
        check = text(check, 64);
        description = text(description, 255);
        violations = text(violations, 32);
        verbose = text(verbose, 1024);
    }

    private static String text(String value, int maximum) {
        value = Objects.requireNonNullElse(value, "");
        int end = Math.min(value.length(), maximum);
        if (end < value.length() && end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end).replaceAll("\\p{Cntrl}", " ");
    }
}
