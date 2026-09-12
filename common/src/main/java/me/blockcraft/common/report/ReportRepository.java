package me.blockcraft.common.report;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.RowMapper;

import java.util.List;

/** The {@code Reports} table, written by the proxy and reviewed on the Paper servers. */
public final class ReportRepository {

    private static final RowMapper<Report> REPORT = row -> new Report(row.getInt("ID"), row.getString("Reportername"),
            row.getString("Reportedname"), row.getString("Grund"), row.getString("Server"), row.getString("Status"));

    private final Database db;

    public ReportRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS Reports (ID INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "Reportername VARCHAR(100), Reportedname VARCHAR(100), Grund VARCHAR(1000), Server VARCHAR(100), Status VARCHAR(100))");
    }

    public List<Report> all() {
        return db.queryList("SELECT * FROM Reports ORDER BY ID", REPORT);
    }

    public List<Report> byReported(String name) {
        return db.queryList("SELECT * FROM Reports WHERE Reportedname = ? ORDER BY ID", REPORT, name);
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
        db.update("INSERT INTO Reports (Reportername, Reportedname, Grund, Server, Status) VALUES (?, ?, ?, ?, 'Offen')",
                reporter, reported, reason, server);
    }

    public void updateStatus(int id, String status) {
        db.update("UPDATE Reports SET Status = ? WHERE ID = ?", status, id);
    }

    public void delete(int id) {
        db.update("DELETE FROM Reports WHERE ID = ?", id);
    }
}
