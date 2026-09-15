package me.blockcraft.common.report;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import me.blockcraft.common.player.PlayerReference;

public record Report(int id, String reporter, String reported, String reason, String server, String status,
                     Instant createdAt, Optional<String> claimedBy, Optional<UUID> reporterId,
                     Optional<UUID> reportedId, Optional<UUID> claimedId) {

    public PlayerReference target() { return new PlayerReference(reported, reportedId); }

    public Report withStatus(String newStatus) {
        return new Report(id, reporter, reported, reason, server, newStatus, createdAt, claimedBy, reporterId, reportedId, claimedId);
    }

    public boolean isClaimedBy(UUID staffId, String staff) {
        return claimedId.map(staffId::equals).orElseGet(() -> claimedBy.filter(holder -> holder.equalsIgnoreCase(staff)).isPresent());
    }
}
