package me.blockcraft.common.moderation;

import java.time.Instant;
import java.util.UUID;

public record KnownPlayer(UUID uuid, String name, Instant lastSeen) {
}
