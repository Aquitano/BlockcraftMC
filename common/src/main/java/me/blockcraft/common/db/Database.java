package me.blockcraft.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private boolean migrated;

    private Database(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database connect(DatabaseConfig config, String poolName) {
        var hikari = new HikariConfig();
        hikari.setPoolName(poolName);
        hikari.setJdbcUrl(config.jdbcUrl());
        // DriverManager cannot see drivers on a plugin classloader, so name the class explicitly.
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setConnectionTimeout(config.connectionTimeout().toMillis());
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        return new Database(new HikariDataSource(hikari));
    }

    public void execute(String ddl) {
        update(ddl);
    }

    /** Brings the shared {@code bc_*} tables up to date; the runtime plugin calls it once before any repository runs. */
    public synchronized void migrate() {
        if (migrated) {
            return;
        }
        try (var connection = dataSource.getConnection()) {
            Migrations.apply(connection, SharedSchema.MIGRATIONS);
            migrated = true;
        } catch (SQLException e) {
            throw new DatabaseException("shared schema migration", e);
        }
    }

    @FunctionalInterface
    public interface Transaction<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Commits when {@code work} returns and rolls back when it throws, whatever it throws. */
    public <T> T transaction(Transaction<T> work) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error e) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("transaction", e);
        }
    }

    public int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return update(connection, sql, params);
        } catch (SQLException e) {
            throw new DatabaseException(sql, e);
        }
    }

    /** The same statement on the connection of a {@link #transaction}, so it commits or rolls back with it. */
    public int update(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        }
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = prepare(connection, sql, params);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.ofNullable(mapper.map(rows)) : Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException(sql, e);
        }
    }

    public <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = prepare(connection, sql, params);
             ResultSet rows = statement.executeQuery()) {
            var result = new ArrayList<T>();
            while (rows.next()) {
                result.add(mapper.map(rows));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException(sql, e);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
