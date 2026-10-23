package me.blockcraft.common.report;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.db.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.blockcraft.common.player.PlayerReference;

/** The {@code Reports} table, written by the proxy and reviewed on the Paper servers. */
public final class ReportRepository {

    /** The age is measured on the database clock, so the reading server's time zone cannot shift it. */
    private static final String COLUMNS = "ID, Reportername, Reportedname, Grund, Server, Status, claimed_by, reporter_uuid, reported_uuid, claimed_uuid, "
            + "TIMESTAMPDIFF(SECOND, created_at, CURRENT_TIMESTAMP) AS age_seconds";

    /** Whoever still has to look at a report is shown it first, newest one on top. */
    private static final String ORDER = " ORDER BY claimed_by IS NULL DESC, ID DESC";

    private static final RowMapper<Report> REPORT = row -> new Report(row.getInt("ID"), row.getString("Reportername"),
            row.getString("Reportedname"), row.getString("Grund"), row.getString("Server"), row.getString("Status"),
            Instant.now().minusSeconds(row.getLong("age_seconds")), Optional.ofNullable(row.getString("claimed_by")),
            Optional.ofNullable(row.getString("reporter_uuid")).map(UUID::fromString),
            Optional.ofNullable(row.getString("reported_uuid")).map(UUID::fromString),
            Optional.ofNullable(row.getString("claimed_uuid")).map(UUID::fromString));

    private final Database db;

    public ReportRepository(Database db) {
        this.db = db;
    }

    /** The open reports; a closed one stays in the table as a record but leaves every list and count. */
    public List<Report> all() {
        return db.queryList("SELECT " + COLUMNS + " FROM Reports WHERE closed_at IS NULL" + ORDER, REPORT);
    }

    public List<Report> byReported(String name) {
        return db.queryList("SELECT " + COLUMNS + " FROM Reports WHERE closed_at IS NULL AND Reportedname = ?" + ORDER, REPORT, name);
    }

    /** Empty once the report is closed, so a menu opened before it was closed cannot act on it. */
    public Optional<Report> byId(int id) {
        return db.queryOne("SELECT " + COLUMNS + " FROM Reports WHERE ID = ? AND closed_at IS NULL", REPORT, id);
    }

    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM Reports WHERE closed_at IS NULL", row -> row.getInt(1)).orElse(0);
    }

    /** Rows written before UUID storage still count when they carry the player's current name. */
    public int countByReported(UUID reported, String name) {
        return db.queryOne("SELECT COUNT(*) FROM Reports WHERE closed_at IS NULL "
                        + "AND (reported_uuid = ? OR (reported_uuid IS NULL AND Reportedname = ?))",
                row -> row.getInt(1), reported.toString(), name).orElse(0);
    }

    /**
     * One open report per reporter and reported player: a unique index over the open ones prevents concurrent
     * submissions, and names also match reports written before UUID storage. A closed report allows a new one.
     */
    public boolean insert(PlayerReference reporter, PlayerReference reported, String reason, String server) {
        try {
            return db.update("INSERT INTO Reports (Reportername, Reportedname, reporter_uuid, reported_uuid, Grund, Server, Status) "
                        + "SELECT ?, ?, ?, ?, ?, ?, 'Offen' WHERE NOT EXISTS (SELECT 1 FROM Reports "
                        + "WHERE closed_at IS NULL AND Reportername = ? AND Reportedname = ? AND (reporter_uuid IS NULL OR reported_uuid IS NULL))",
                reporter.name(), reported.name(), reporter.uuid().orElseThrow().toString(), reported.uuid().orElseThrow().toString(),
                reason, server, reporter.name(), reported.name()) > 0;
        } catch (DatabaseException e) {
            if (e.getCause() instanceof java.sql.SQLException sql && sql.getErrorCode() == 1062) return false;
            throw e;
        }
    }

    public void updateStatus(int id, String status) {
        db.update("UPDATE Reports SET Status = ? WHERE ID = ?", status, id);
    }

    /** Only a free report can be taken, so two moderators clicking at the same moment cannot both hold it. */
    public boolean claim(int id, UUID staffId, String staff) {
        return db.update("UPDATE Reports SET claimed_by = ?, claimed_uuid = ? WHERE ID = ? AND claimed_by IS NULL", staff, staffId.toString(), id) > 0;
    }

    public void release(int id, UUID staffId, String staff) {
        db.update("UPDATE Reports SET claimed_by = NULL, claimed_uuid = NULL WHERE ID = ? AND "
                + "(claimed_uuid = ? OR (claimed_uuid IS NULL AND claimed_by = ?))", id, staffId.toString(), staff);
    }

    public void close(int id, UUID staffId, String staff) {
        db.update("UPDATE Reports SET closed_at = CURRENT_TIMESTAMP, closed_by = ?, closed_uuid = ? WHERE ID = ? AND closed_at IS NULL",
                staff, staffId.toString(), id);
    }
}
