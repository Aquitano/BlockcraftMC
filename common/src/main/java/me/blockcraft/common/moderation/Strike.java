package me.blockcraft.common.moderation;

import java.time.Instant;
import java.util.UUID;

public record Strike(long id, UUID player, String reason, String issuer, Instant createdAt) {
}
