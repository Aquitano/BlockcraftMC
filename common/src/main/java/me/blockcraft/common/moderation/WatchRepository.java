package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.time.Duration;
import java.util.UUID;

/**
 * Whether a player logging in deserves a second look: open reports against them, a punishment from lately, or an
 * address shared with an account that is banned right now.
 */
public final class WatchRepository {

    public record Watch(int openReports, int recentPunishments, int bannedAlts) {

        public boolean hasRecord() {
            return openReports > 0 || recentPunishments > 0;
        }
    }

    private static final Watch NOTHING = new Watch(0, 0, 0);

    private final Database db;

    public WatchRepository(Database db) {
        this.db = db;
    }

    /** One round trip per login; reports are keyed by name, punishments by UUID. */
    public Watch of(UUID player, String name, Duration recent, Duration addressRetention) {
        return db.queryOne("SELECT (SELECT COUNT(*) FROM Reports WHERE closed_at IS NULL "
                        + "AND (reported_uuid = ? OR (reported_uuid IS NULL AND Reportedname = ?))) AS reports, "
                        + "(SELECT COUNT(*) FROM bc_punishments WHERE player_uuid = ? AND created_at > NOW() - INTERVAL ? SECOND) AS punishments, "
                        + "(" + PlayerAddressRepository.bannedAltsQuery() + ") AS banned_alts",
                row -> new Watch(row.getInt("reports"), row.getInt("punishments"), row.getInt("banned_alts")),
                player.toString(), name, player.toString(), recent.toSeconds(),
                player.toString(), addressRetention.toSeconds(), addressRetention.toSeconds()).orElse(NOTHING);
    }
}
