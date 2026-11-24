package me.blockcraft.common.db;

import java.sql.SQLException;

public final class DatabaseException extends RuntimeException {

    DatabaseException(String sql, SQLException cause) {
        super("SQL failed: " + sql, cause);
    }
}
