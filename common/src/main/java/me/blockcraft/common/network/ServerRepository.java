package me.blockcraft.common.network;

import me.blockcraft.common.db.Database;

import java.util.List;

/**
 * Backend servers announce themselves here and the proxy registers whatever is alive, so no server list is
 * maintained by hand. Timestamps come from the database clock so the servers' clocks need not agree.
 */
public final class ServerRepository {

    private final Database db;

    public ServerRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("""
                CREATE TABLE IF NOT EXISTS bc_servers (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    group_name VARCHAR(64) NOT NULL,
                    host VARCHAR(255) NOT NULL,
                    port INT NOT NULL,
                    state VARCHAR(16) NOT NULL,
                    online INT NOT NULL,
                    max_players INT NOT NULL,
                    heartbeat TIMESTAMP NOT NULL
                )""");
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
        return db.queryList("""
                SELECT name, group_name, host, port, state, online, max_players FROM bc_servers
                WHERE heartbeat > CURRENT_TIMESTAMP - INTERVAL ? SECOND""",
                row -> new ServerRecord(
                        new ServerIdentity(row.getString("name"), row.getString("group_name"), row.getString("host"), row.getInt("port")),
                        new ServerStatus(ServerState.valueOf(row.getString("state")), row.getInt("online"), row.getInt("max_players"))),
                staleAfterSeconds);
    }
}
