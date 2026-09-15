package me.blockcraft.common.report;

import java.time.Instant;
import java.util.Optional;

public record Report(int id, String reporter, String reported, String reason, String server, String status,
                     Instant createdAt, Optional<String> claimedBy) {

    public Report withStatus(String newStatus) {
        return new Report(id, reporter, reported, reason, server, newStatus, createdAt, claimedBy);
    }

    public boolean isClaimedBy(String staff) {
        return claimedBy.filter(holder -> holder.equalsIgnoreCase(staff)).isPresent();
    }
}
