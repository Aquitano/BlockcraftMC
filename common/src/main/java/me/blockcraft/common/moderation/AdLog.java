package me.blockcraft.common.moderation;

import java.time.Instant;
import java.util.UUID;

public record AdLog(long id, UUID player, String server, String message, Instant createdAt) {
}
