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
        hikari.setMaximumPoolSize(4);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        return new Database(new HikariDataSource(hikari));
    }

    public void execute(String ddl) {
        update(ddl);
    }

    public int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(sql, e);
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
