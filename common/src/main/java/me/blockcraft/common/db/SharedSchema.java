package me.blockcraft.common.db;

import java.util.List;

/**
 * The shared {@code bc_*} tables and the legacy {@code Reports} and {@code VCOINS} tables, as ordered migrations.
 * Append a new version for every change; a deployed version is checksummed and must never be edited.
 */
final class SharedSchema {

    static final List<Migration> MIGRATIONS = List.of(
        new Migration(1, List.of(
            "CREATE TABLE IF NOT EXISTS bc_discord_links ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "code CHAR(6) NULL, "
                + "code_expires_at TIMESTAMP NULL, "
                + "discord_id VARCHAR(32) NULL, "
                + "linked_at TIMESTAMP NULL, "
                + "UNIQUE KEY uq_bc_discord_links_discord (discord_id))",
            "CREATE TABLE IF NOT EXISTS bc_ad_log ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "server VARCHAR(64) NOT NULL, "
                + "message TEXT NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS bc_frozen ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "issuer VARCHAR(16) NOT NULL, "
                + "since TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS bc_players ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, "
                + "first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "playtime_seconds BIGINT NOT NULL DEFAULT 0, "
                + "INDEX idx_bc_players_name (name))",
            "ALTER TABLE bc_players ADD COLUMN IF NOT EXISTS first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE bc_players ADD COLUMN IF NOT EXISTS playtime_seconds BIGINT NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS bc_punishment_catalog ("
                + "number INT NOT NULL PRIMARY KEY, "
                + "type VARCHAR(8) NOT NULL, "
                + "reason VARCHAR(255) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS bc_punishments ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "type ENUM('BAN','MUTE') NOT NULL, "
                + "reason TEXT NOT NULL, "
                + "issuer VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "expires_at TIMESTAMP NULL, "
                + "revoked_at TIMESTAMP NULL, "
                + "revoked_by VARCHAR(16) NULL, "
                + "INDEX idx_bc_punishments_player (player_uuid, type))",
            "CREATE TABLE IF NOT EXISTS bc_settings (`key` VARCHAR(64) NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS bc_strikes ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "reason TEXT NOT NULL, "
                + "issuer VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_bc_strikes_player (player_uuid))",
            "CREATE TABLE IF NOT EXISTS bc_vanished ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "since TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS bc_warns ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "reason TEXT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_bc_warns_player (player_uuid))",
            """
                CREATE TABLE IF NOT EXISTS bc_servers (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    group_name VARCHAR(64) NOT NULL,
                    host VARCHAR(255) NOT NULL,
                    port INT NOT NULL,
                    state VARCHAR(16) NOT NULL,
                    online INT NOT NULL,
                    max_players INT NOT NULL,
                    heartbeat TIMESTAMP NOT NULL
                )""",
            "CREATE TABLE IF NOT EXISTS bc_nicks ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "nick VARCHAR(16) NOT NULL, "
                + "skin_owner VARCHAR(16) NOT NULL, "
                + "set_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS Reports (ID INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "Reportername VARCHAR(100), Reportedname VARCHAR(100), Grund VARCHAR(1000), Server VARCHAR(100), Status VARCHAR(100), "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, claimed_by VARCHAR(100))",
            "ALTER TABLE Reports ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE Reports ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(100)",
            "CREATE TABLE IF NOT EXISTS bc_player_settings ("
                + "player_uuid CHAR(36) NOT NULL, "
                + "setting VARCHAR(32) NOT NULL, "
                + "value VARCHAR(64) NOT NULL, "
                + "PRIMARY KEY (player_uuid, setting))",
            "CREATE TABLE IF NOT EXISTS bc_xp ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "xp BIGINT NOT NULL DEFAULT 0)"
        )),
        new Migration(2, List.of(
            "CREATE TABLE IF NOT EXISTS VCOINS (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), COINS INT NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS bc_unlocks (player_uuid CHAR(36) NOT NULL, item VARCHAR(64) NOT NULL, "
                + "unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (player_uuid, item))"
        )),
        new Migration(3, List.of(
            "ALTER TABLE Reports ADD COLUMN IF NOT EXISTS reporter_uuid CHAR(36) NULL",
            "ALTER TABLE Reports ADD COLUMN IF NOT EXISTS reported_uuid CHAR(36) NULL",
            "ALTER TABLE Reports ADD COLUMN IF NOT EXISTS claimed_uuid CHAR(36) NULL",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_reports_accounts ON Reports (reporter_uuid, reported_uuid)"
        )),
        new Migration(4, List.of(
            "ALTER TABLE bc_punishments ADD COLUMN IF NOT EXISTS issuer_uuid CHAR(36) NULL",
            "ALTER TABLE bc_punishments ADD COLUMN IF NOT EXISTS revoked_by_uuid CHAR(36) NULL",
            "ALTER TABLE bc_strikes ADD COLUMN IF NOT EXISTS issuer_uuid CHAR(36) NULL",
            "ALTER TABLE bc_frozen ADD COLUMN IF NOT EXISTS issuer_uuid CHAR(36) NULL"
        )),
        new Migration(5, List.of(
            "ALTER TABLE bc_nicks ADD COLUMN IF NOT EXISTS rank_group VARCHAR(36) NOT NULL DEFAULT 'default'"
        )),
        new Migration(6, List.of(
            "CREATE TABLE IF NOT EXISTS bc_round_recaps ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "game VARCHAR(32) NOT NULL, "
                + "won TINYINT(1) NOT NULL, "
                + "winners VARCHAR(255) NOT NULL, "
                + "kills INT NOT NULL, "
                + "deaths INT NOT NULL, "
                + "best_streak INT NOT NULL, "
                + "coins INT NOT NULL, "
                + "xp INT NOT NULL, "
                + "ended_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        )),
        new Migration(7, List.of(
            "CREATE TABLE IF NOT EXISTS bc_elo ("
                + "player_uuid CHAR(36) NOT NULL, "
                + "season VARCHAR(16) NOT NULL, "
                + "rating INT NOT NULL, "
                + "wins INT NOT NULL DEFAULT 0, "
                + "losses INT NOT NULL DEFAULT 0, "
                + "name VARCHAR(16) NOT NULL, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, season))"
        ))
    );

    private SharedSchema() {
    }
}
