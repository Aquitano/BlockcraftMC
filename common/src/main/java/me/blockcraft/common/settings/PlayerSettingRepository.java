package me.blockcraft.common.settings;

import me.blockcraft.common.db.Database;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code bc_player_settings}: one row per player and setting, absent while the player still has the default. */
public final class PlayerSettingRepository {

    private final Database db;

    public PlayerSettingRepository(Database db) {
        this.db = db;
    }

    public Map<PlayerSetting, String> all(UUID player) {
        var stored = new EnumMap<PlayerSetting, String>(PlayerSetting.class);
        db.queryList("SELECT setting, value FROM bc_player_settings WHERE player_uuid = ?",
                        row -> Map.entry(row.getString("setting"), row.getString("value")), player.toString())
                .forEach(row -> PlayerSetting.byKey(row.getKey()).ifPresent(setting -> stored.put(setting, row.getValue())));
        return stored;
    }

    public Optional<String> get(UUID player, PlayerSetting setting) {
        return db.queryOne("SELECT value FROM bc_player_settings WHERE player_uuid = ? AND setting = ?",
                row -> row.getString("value"), player.toString(), setting.key());
    }

    public void set(UUID player, PlayerSetting setting, String value) {
        db.update("INSERT INTO bc_player_settings (player_uuid, setting, value) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE value = VALUES(value)", player.toString(), setting.key(), value);
    }

    public void clear(UUID player, PlayerSetting setting) {
        db.update("DELETE FROM bc_player_settings WHERE player_uuid = ? AND setting = ?", player.toString(), setting.key());
    }
}
