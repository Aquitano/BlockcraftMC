package me.blockcraft.common.db;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet row) throws SQLException;
}
