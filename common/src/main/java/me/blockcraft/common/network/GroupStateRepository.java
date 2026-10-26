package me.blockcraft.common.network;

import me.blockcraft.common.db.Database;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** {@code bc_server_groups}: the proxy writes each on-demand group's state, every Paper server reads them all. */
public final class GroupStateRepository {

    private final Database db;

    public GroupStateRepository(Database db) {
        this.db = db;
    }

    /** {@code since} moves only when the state changes, so it tells how long a group has been asleep or starting. */
    public void put(String group, GroupState state) {
        db.update("""
                INSERT INTO bc_server_groups (group_name, state, since) VALUES (?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE since = IF(state = VALUES(state), since, CURRENT_TIMESTAMP), state = VALUES(state)""",
                group, state.name());
    }

    /** Forgets every group outside {@code groups}, so a group taken off on-demand, or the feature turned off, shows plainly again. */
    public void retain(Set<String> groups) {
        for (var group : all().keySet()) {
            if (!groups.contains(group)) {
                db.update("DELETE FROM bc_server_groups WHERE group_name = ?", group);
            }
        }
    }

    /** A state this build does not know is left out, as if the group were not on demand. */
    public Map<String, GroupState> all() {
        var states = new HashMap<String, GroupState>();
        for (var row : db.queryList("SELECT group_name, state FROM bc_server_groups",
                result -> Map.entry(result.getString("group_name"), result.getString("state")))) {
            try {
                states.put(row.getKey(), GroupState.valueOf(row.getValue()));
            } catch (IllegalArgumentException unknown) {
                // A newer proxy may know states this server does not.
            }
        }
        return states;
    }
}
