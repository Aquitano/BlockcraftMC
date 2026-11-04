package me.blockcraft.common.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record KnownPlayer(UUID uuid, String name, Instant firstSeen, Instant lastSeen, Duration playtime) {
}
