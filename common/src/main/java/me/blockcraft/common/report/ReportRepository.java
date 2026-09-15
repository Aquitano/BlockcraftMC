package me.blockcraft.common.report;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The {@code Reports} table, written by the proxy and reviewed on the Paper servers. */
public final class ReportRepository {

    /** The age is measured on the database clock, so the reading server's time zone cannot shift it. */
    private static final String COLUMNS = "ID, Reportername, Reportedname, Grund, Server, Status, claimed_by, "
            + "TIMESTAMPDIFF(SECOND, created_at, CURRENT_TIMESTAMP) AS age_seconds";

    /** Whoever still has to look at a report is shown it first, newest one on top. */
    private static final String ORDER = " ORDER BY claimed_by IS NULL DESC, ID DESC";

    private static final RowMapper<Report> REPORT = row -> new Report(row.getInt("ID"), row.getString("Reportername"),
            row.getString("Reportedname"), row.getString("Grund"), row.getString("Server"), row.getString("Status"),
            Instant.now().minusSeconds(row.getLong("age_seconds")), Optional.ofNullable(row.getString("claimed_by")));

    private final Database db;

    public ReportRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS Reports (ID INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "Reportername VARCHAR(100), Reportedname VARCHAR(100), Grund VARCHAR(1000), Server VARCHAR(100), Status VARCHAR(100), "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, claimed_by VARCHAR(100))");
        db.execute("ALTER TABLE Reports ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        db.execute("ALTER TABLE Reports ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(100)");
    }

    public List<Report> all() {
        return db.queryList("SELECT " + COLUMNS + " FROM Reports" + ORDER, REPORT);
    }

    public List<Report> byReported(String name) {
        return db.queryList("SELECT " + COLUMNS + " FROM Reports WHERE Reportedname = ?" + ORDER, REPORT, name);
    }

    public Optional<Report> byId(int id) {
        return db.queryOne("SELECT " + COLUMNS + " FROM Reports WHERE ID = ?", REPORT, id);
    }

    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM Reports", row -> row.getInt(1)).orElse(0);
    }

    public int countByReported(String reported) {
        return db.queryOne("SELECT COUNT(*) FROM Reports WHERE Reportedname = ?", row -> row.getInt(1), reported).orElse(0);
    }

    public boolean exists(String reporter, String reported) {
        return db.queryOne("SELECT ID FROM Reports WHERE Reportername = ? AND Reportedname = ?", row -> row.getInt("ID"), reporter, reported)
                .isPresent();
    }

    public void insert(String reporter, String reported, String reason, String server) {
        db.update("INSERT INTO Reports (Reportername, Reportedname, Grund, Server, Status, created_at) "
                + "VALUES (?, ?, ?, ?, 'Offen', CURRENT_TIMESTAMP)", reporter, reported, reason, server);
    }

    public void updateStatus(int id, String status) {
        db.update("UPDATE Reports SET Status = ? WHERE ID = ?", status, id);
    }

    /** Only a free report can be taken, so two moderators clicking at the same moment cannot both hold it. */
    public boolean claim(int id, String staff) {
        return db.update("UPDATE Reports SET claimed_by = ? WHERE ID = ? AND claimed_by IS NULL", staff, id) > 0;
    }

    public void release(int id, String staff) {
        db.update("UPDATE Reports SET claimed_by = NULL WHERE ID = ? AND claimed_by = ?", id, staff);
    }

    public void delete(int id) {
        db.update("DELETE FROM Reports WHERE ID = ?", id);
    }
}
