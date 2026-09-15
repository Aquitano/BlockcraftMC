package me.blockcraft.common.db;

import me.blockcraft.common.report.ReportRepository;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Runs against the MariaDB named by {@code BLOCKCRAFT_TEST_DB_PORT} and is skipped without it. Each test creates
 * and drops its own {@code bc_test_*} schema.
 */
public class DatabaseIntegrationTest {

    private static final String HOST = "127.0.0.1";
    private static final String USER = "root";
    private static final int APPLIED = SharedSchema.MIGRATIONS.size();
    private static final int NEXT = APPLIED + 1;

    private int port;
    private String password;
    private String url;
    private String schema;
    private Database db;

    @Before
    public void createSchema() throws SQLException {
        String configuredPort = System.getenv("BLOCKCRAFT_TEST_DB_PORT");
        Assume.assumeTrue("Set BLOCKCRAFT_TEST_DB_PORT to run MariaDB integration tests", configuredPort != null);
        port = Integer.parseInt(configuredPort);
        password = System.getenv().getOrDefault("BLOCKCRAFT_TEST_DB_PASSWORD", "blockcraft-test");
        url = "jdbc:mariadb://" + HOST + ":" + port + "/";
        schema = "bc_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, USER, password); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema);
        }
        db = connect("test");
    }

    @After
    public void dropSchema() throws SQLException {
        if (db != null) {
            db.close();
        }
        if (schema != null) {
            try (var connection = DriverManager.getConnection(url, USER, password); var sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    private Database connect(String poolName) {
        return Database.connect(new DatabaseConfig(HOST, port, schema, USER, password), poolName);
    }

    private int count(String sql, Object... params) {
        return db.queryOne(sql, row -> row.getInt(1), params).orElseThrow();
    }

    @Test
    public void upgradesLegacyReportsWithoutLosingRowsAndRunsOnce() {
        db.execute("CREATE TABLE Reports (ID INT AUTO_INCREMENT PRIMARY KEY, Reportername VARCHAR(100), Reportedname VARCHAR(100), "
                + "Grund VARCHAR(1000), Server VARCHAR(100), Status VARCHAR(100))");
        db.update("INSERT INTO Reports (Reportername, Reportedname, Grund, Server, Status) VALUES ('Reporter', 'Target', 'Reason', 'lobby', 'Offen')");
        db.migrate();
        db.migrate();
        var report = new ReportRepository(db).all().getFirst();
        assertEquals("Reason", report.reason());
        assertEquals(APPLIED, count("SELECT COUNT(*) FROM bc_schema_migrations"));
    }

    @Test
    public void concurrentStartupSerializesMigrations() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var starts = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 4; i++) {
                starts.add(() -> {
                    try (var other = connect("parallel")) {
                        other.migrate();
                    }
                    return null;
                });
            }
            for (var start : pool.invokeAll(starts)) {
                start.get();
            }
        }
        assertEquals(APPLIED, count("SELECT COUNT(*) FROM bc_schema_migrations"));
    }

    @Test
    public void failedMigrationCanResumeAndAppliedChecksumsCannotChange() throws Exception {
        db.migrate();
        try (var connection = DriverManager.getConnection(url + schema, USER, password)) {
            String probe = "CREATE TABLE IF NOT EXISTS bc_probe (id INT PRIMARY KEY)";
            assertThrows(SQLException.class, () -> Migrations.apply(connection, List.of(new Migration(NEXT, List.of(probe, "INVALID SQL")))));
            assertEquals(0, count("SELECT COUNT(*) FROM bc_schema_migrations WHERE version = ?", NEXT));
            Migrations.apply(connection, List.of(new Migration(NEXT, List.of(probe))));
            assertThrows(SQLException.class, () -> Migrations.apply(connection, List.of(new Migration(NEXT, List.of("SELECT 1")))));
        }
    }
}
