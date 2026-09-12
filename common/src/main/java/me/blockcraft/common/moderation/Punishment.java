package me.blockcraft.common.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record Punishment(long id, UUID player, PunishmentType type, String reason, String issuer, Instant createdAt,
                         Optional<Instant> expiresAt, Optional<Instant> revokedAt, Optional<String> revokedBy) {

    public boolean isPermanent() {
        return expiresAt.isEmpty();
    }

    /** Empty for permanent punishments; zero once expired. */
    public Optional<Duration> remaining() {
        return expiresAt.map(end -> {
            var left = Duration.between(Instant.now(), end);
            return left.isNegative() ? Duration.ZERO : left;
        });
    }
}
