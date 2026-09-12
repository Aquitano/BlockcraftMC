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
                    host VARCHAR(255) NOT NULL,
                    port INT NOT NULL,
                    heartbeat TIMESTAMP NOT NULL
                )""");
    }

    public void heartbeat(ServerAddress server) {
        db.update("""
                INSERT INTO bc_servers (name, host, port, heartbeat) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE host = VALUES(host), port = VALUES(port), heartbeat = CURRENT_TIMESTAMP""",
                server.name(), server.host(), server.port());
    }

    public void delete(String name) {
        db.update("DELETE FROM bc_servers WHERE name = ?", name);
    }

    public List<ServerAddress> alive(int staleAfterSeconds) {
        return db.queryList("SELECT name, host, port FROM bc_servers WHERE heartbeat > CURRENT_TIMESTAMP - INTERVAL ? SECOND",
                row -> new ServerAddress(row.getString("name"), row.getString("host"), row.getInt("port")),
                staleAfterSeconds);
    }
}
