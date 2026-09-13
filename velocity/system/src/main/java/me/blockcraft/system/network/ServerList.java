package me.blockcraft.system.network;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;

import java.time.Duration;
import java.util.List;

/**
 * Every row of {@code bc_servers} with the age of its heartbeat, stale ones included; the repository the
 * discovery task uses reports only the servers that are alive.
 */
public final class ServerList {

    public record Entry(ServerRecord server, Duration heartbeatAge) {
    }

    private final Database db;

    public ServerList(Database db) {
        this.db = db;
    }

    public List<Entry> all() {
        return db.queryList("""
                SELECT name, group_name, host, port, state, online, max_players,
                       TIMESTAMPDIFF(SECOND, heartbeat, CURRENT_TIMESTAMP) AS heartbeat_age
                FROM bc_servers ORDER BY group_name, name""",
                row -> new Entry(
                        new ServerRecord(
                                new ServerIdentity(row.getString("name"), row.getString("group_name"),
                                        row.getString("host"), row.getInt("port")),
                                new ServerStatus(ServerState.valueOf(row.getString("state")), row.getInt("online"),
                                        row.getInt("max_players"))),
                        Duration.ofSeconds(row.getLong("heartbeat_age"))));
    }
}
