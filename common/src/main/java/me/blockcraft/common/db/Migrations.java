package me.blockcraft.common.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Applies the migrations a database has not recorded yet, under a MariaDB advisory lock so servers starting at the
 * same moment take turns. DDL commits implicitly, so a crash mid-migration leaves it unrecorded and every statement
 * must be safe to run again.
 */
public final class Migrations {

    private static final int LOCK_TIMEOUT_SECONDS = 30;

    private Migrations() {
    }

    public static void apply(Connection connection, List<Migration> migrations) throws SQLException {
        requireOrdered(migrations);
        String lock = "blockcraft-schema:" + connection.getCatalog();
        acquire(connection, lock);
        try {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS bc_schema_migrations (version INT PRIMARY KEY, "
                        + "checksum CHAR(64) NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            }
            for (var migration : migrations) {
                if (!isApplied(connection, migration)) {
                    run(connection, migration);
                }
            }
        } finally {
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lock);
                statement.execute();
            }
        }
    }

    private static void requireOrdered(List<Migration> migrations) {
        int previous = 0;
        for (var migration : migrations) {
            if (migration.version() <= previous) {
                throw new IllegalArgumentException("Migrations must be ordered and unique");
            }
            previous = migration.version();
        }
    }

    private static void acquire(Connection connection, String lock) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lock);
            statement.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) {
                    throw new SQLException("Timed out acquiring schema lock");
                }
            }
        }
    }

    private static boolean isApplied(Connection connection, Migration migration) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT checksum FROM bc_schema_migrations WHERE version = ?")) {
            statement.setInt(1, migration.version());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return false;
                }
                if (!rows.getString(1).equals(migration.checksum())) {
                    throw new SQLException("Deployed migration changed: " + migration.version());
                }
                return true;
            }
        }
    }

    private static void run(Connection connection, Migration migration) throws SQLException {
        for (var sql : migration.statements()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }
        try (var statement = connection.prepareStatement("INSERT INTO bc_schema_migrations (version, checksum) VALUES (?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.checksum());
            statement.executeUpdate();
        }
    }
}
