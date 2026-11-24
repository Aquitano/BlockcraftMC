package me.blockcraft.common.moderation;

import me.blockcraft.common.player.PlayerReference;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Warns from before issuers were recorded have none. */
public record Warn(long id, UUID player, Optional<String> reason, Optional<PlayerReference> issuer, Instant createdAt) {
}
