package me.blockcraft.common.moderation;

import me.blockcraft.common.player.PlayerReference;

import java.time.Instant;
import java.util.UUID;

public record Strike(long id, UUID player, String reason, PlayerReference issuer, Instant createdAt) {
}
