package me.blockcraft.common.network;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

/**
 * Backend servers announce themselves here and the proxy registers whatever is alive, so no server list is
 * maintained by hand. Timestamps come from the database clock so the servers' clocks need not agree.
 */
public final class ServerRepository {

    /** One row of {@code bc_servers} with the age of its heartbeat, stale or not. */
    public record Entry(ServerRecord server, Duration heartbeatAge) {
    }

    private static final String COLUMNS = "name, group_name, host, port, state, online, max_players, "
            + "TIMESTAMPDIFF(SECOND, heartbeat, CURRENT_TIMESTAMP) AS heartbeat_age";

    private final Database db;

    public ServerRepository(Database db) {
        this.db = db;
    }

    public void heartbeat(ServerIdentity server, ServerStatus status) {
        db.update("""
                INSERT INTO bc_servers (name, group_name, host, port, state, online, max_players, heartbeat)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), host = VALUES(host), port = VALUES(port),
                    state = VALUES(state), online = VALUES(online), max_players = VALUES(max_players),
                    heartbeat = CURRENT_TIMESTAMP""",
                server.name(), server.group(), server.host(), server.port(),
                status.state().name(), status.online(), status.maxPlayers());
    }

    public void delete(String name) {
        db.update("DELETE FROM bc_servers WHERE name = ?", name);
    }

    public List<ServerRecord> alive(int staleAfterSeconds) {
        return db.queryList("SELECT " + COLUMNS + " FROM bc_servers WHERE heartbeat > CURRENT_TIMESTAMP - INTERVAL ? SECOND",
                row -> record(row), staleAfterSeconds);
    }

    /** Every announced server, stale ones included, for the staff overview. */
    public List<Entry> all() {
        return db.queryList("SELECT " + COLUMNS + " FROM bc_servers ORDER BY group_name, name",
                row -> new Entry(record(row), Duration.ofSeconds(row.getLong("heartbeat_age"))));
    }

    private static ServerRecord record(ResultSet row) throws SQLException {
        return new ServerRecord(
                new ServerIdentity(row.getString("name"), row.getString("group_name"), row.getString("host"), row.getInt("port")),
                new ServerStatus(ServerState.valueOf(row.getString("state")), row.getInt("online"), row.getInt("max_players")));
    }
}
