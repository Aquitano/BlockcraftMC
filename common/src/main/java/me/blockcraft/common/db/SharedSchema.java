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
        )),
        new Migration(8, List.of(
            "ALTER TABLE bc_warns ADD COLUMN IF NOT EXISTS issuer VARCHAR(16) NULL",
            "ALTER TABLE bc_warns ADD COLUMN IF NOT EXISTS issuer_uuid CHAR(36) NULL"
        )),
        new Migration(9, List.of(
            "CREATE TABLE IF NOT EXISTS bc_staff_actions ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "issuer_uuid CHAR(36) NULL, "
                + "issuer_name VARCHAR(16) NOT NULL, "
                + "action VARCHAR(32) NOT NULL, "
                + "target_uuid CHAR(36) NULL, "
                + "target_name VARCHAR(16) NULL, "
                + "detail TEXT NULL, "
                + "server VARCHAR(64) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_bc_staff_actions_issuer (issuer_uuid, created_at), "
                + "INDEX idx_bc_staff_actions_target (target_uuid, created_at), "
                + "INDEX idx_bc_staff_actions_created (created_at))"
        )),
        new Migration(10, List.of(
            "CREATE TABLE IF NOT EXISTS bc_player_addresses ("
                + "player_uuid CHAR(36) NOT NULL, "
                + "address_hash BINARY(32) NOT NULL, "
                + "first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, address_hash), "
                + "INDEX idx_bc_player_addresses_hash (address_hash))"
        )),
        new Migration(11, List.of(
            "ALTER TABLE bc_servers ADD COLUMN IF NOT EXISTS tps DECIMAL(4,2) NOT NULL DEFAULT 20",
            "ALTER TABLE bc_servers ADD COLUMN IF NOT EXISTS mspt DECIMAL(8,2) NOT NULL DEFAULT 0"
        )),
        new Migration(12, List.of(
            "CREATE TABLE IF NOT EXISTS bc_ignores ("
                + "player_uuid CHAR(36) NOT NULL, "
                + "ignored_uuid CHAR(36) NOT NULL, "
                + "ignored_name VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, ignored_uuid))"
        )),
        new Migration(13, List.of(
            "ALTER TABLE bc_servers ADD COLUMN IF NOT EXISTS occupancy INT NOT NULL DEFAULT 0"
        )),
        new Migration(14, List.of(
            "ALTER TABLE bc_players ADD COLUMN IF NOT EXISTS join_number INT NULL",
            "UPDATE bc_players p JOIN (SELECT uuid, ROW_NUMBER() OVER (ORDER BY first_seen, uuid) AS n FROM bc_players) ranked "
                + "ON p.uuid = ranked.uuid SET p.join_number = ranked.n WHERE p.join_number IS NULL",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_bc_players_join_number ON bc_players (join_number)",
            "CREATE TABLE IF NOT EXISTS bc_greetings ("
                + "greeter_uuid CHAR(36) NOT NULL, "
                + "newcomer_uuid CHAR(36) NOT NULL, "
                + "greeter_name VARCHAR(16) NOT NULL, "
                + "greeted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (greeter_uuid, newcomer_uuid), "
                + "INDEX idx_bc_greetings_newcomer (newcomer_uuid, greeted_at))",
            "CREATE TABLE IF NOT EXISTS bc_rivalries ("
                + "killer_uuid CHAR(36) NOT NULL, "
                + "victim_uuid CHAR(36) NOT NULL, "
                + "kills INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (killer_uuid, victim_uuid), "
                + "INDEX idx_bc_rivalries_victim (victim_uuid))"
        )),
        new Migration(15, List.of(
            "CREATE TABLE IF NOT EXISTS bc_join_counter (id INT NOT NULL PRIMARY KEY, next_number INT NOT NULL)",
            "INSERT IGNORE INTO bc_join_counter (id, next_number) SELECT 1, COALESCE(MAX(join_number), 0) + 1 FROM bc_players",
            "CREATE TABLE IF NOT EXISTS bc_secret_firsts (item VARCHAR(64) NOT NULL PRIMARY KEY, player_uuid CHAR(36) NOT NULL)",
            "INSERT IGNORE INTO bc_secret_firsts (item, player_uuid) SELECT item, player_uuid FROM "
                + "(SELECT item, player_uuid, ROW_NUMBER() OVER (PARTITION BY item ORDER BY unlocked_at, player_uuid) AS n "
                + "FROM bc_unlocks WHERE item LIKE 'lobby.secret.%') firsts WHERE n = 1",
            "ALTER TABLE bc_players ADD COLUMN IF NOT EXISTS first_seen_verified BOOLEAN NOT NULL DEFAULT FALSE",
            "INSERT IGNORE INTO bc_unlocks (player_uuid, item) SELECT UUID, 'tag.og' FROM VCOINS",
            legacyTags("qsg_stats"), "PREPARE bc_legacy FROM @bc_legacy_sql", "EXECUTE bc_legacy", "DEALLOCATE PREPARE bc_legacy",
            legacyTags("em_stats"), "PREPARE bc_legacy FROM @bc_legacy_sql", "EXECUTE bc_legacy", "DEALLOCATE PREPARE bc_legacy",
            legacyTags("duell_stats"), "PREPARE bc_legacy FROM @bc_legacy_sql", "EXECUTE bc_legacy", "DEALLOCATE PREPARE bc_legacy"
        )),
        new Migration(16, List.of(
            "CREATE INDEX IF NOT EXISTS idx_bc_greetings_daily ON bc_greetings (greeter_uuid, greeted_at)"
        )),
        new Migration(17, List.of(
            "DELETE later FROM bc_nicks later JOIN bc_nicks earlier ON earlier.nick = later.nick "
                + "AND (earlier.set_at, earlier.player_uuid) < (later.set_at, later.player_uuid)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_bc_nicks_nick ON bc_nicks (nick)"
        ))
    );

    private SharedSchema() {
    }

    private static String legacyTags(String table) {
        return "SET @bc_legacy_sql = IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = '" + table + "'), 'INSERT IGNORE INTO bc_unlocks (player_uuid, item) "
                + "SELECT UUID, ''tag.og'' FROM " + table + "', 'DO 0')";
    }
}
