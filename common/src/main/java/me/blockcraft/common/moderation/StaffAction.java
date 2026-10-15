package me.blockcraft.common.moderation;

import me.blockcraft.common.player.PlayerReference;

import java.time.Instant;
import java.util.Optional;

/** One row of the staff audit log: who did what to whom, on which server. */
public record StaffAction(long id, PlayerReference issuer, String action, Optional<PlayerReference> target,
                          Optional<String> detail, String server, Instant createdAt) {
}
