package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.util.Optional;

public final class SettingsRepository {

    private final Database db;

    public SettingsRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_settings (`key` VARCHAR(64) NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)");
    }

    public Optional<String> get(Setting setting) {
        return db.queryOne("SELECT `value` FROM bc_settings WHERE `key` = ?", row -> row.getString("value"), setting.key());
    }

    public void set(Setting setting, String value) {
        db.update("INSERT INTO bc_settings (`key`, `value`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)",
                setting.key(), value);
    }

    public boolean isEnabled(Setting setting) {
        return get(setting).map(Boolean::parseBoolean).orElse(false);
    }

    public void setEnabled(Setting setting, boolean enabled) {
        set(setting, Boolean.toString(enabled));
    }
}
