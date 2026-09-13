package me.blockcraft.common.discord;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The link between a Minecraft and a Discord account. The proxy hands out the code and clears the link; the
 * Discord bot lives outside this repository and only fills in {@code discord_id} and {@code linked_at}.
 */
public final class DiscordLinkRepository {

    public record Link(Optional<String> discordId, Optional<String> pendingCode) {

        public boolean isLinked() {
            return discordId.isPresent();
        }
    }

    private final Database db;

    public DiscordLinkRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_discord_links ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "code CHAR(6) NULL, "
                + "code_expires_at TIMESTAMP NULL, "
                + "discord_id VARCHAR(32) NULL, "
                + "linked_at TIMESTAMP NULL, "
                + "UNIQUE KEY uq_bc_discord_links_discord (discord_id))");
    }

    /** The code expires on the database clock, so an expired one never reaches the caller. */
    public Optional<Link> get(UUID player) {
        return db.queryOne("SELECT discord_id, IF(code_expires_at > NOW(), code, NULL) AS code "
                + "FROM bc_discord_links WHERE player_uuid = ?", DiscordLinkRepository::map, player.toString());
    }

    public void storeCode(UUID player, String code, Duration validity) {
        db.update("INSERT INTO bc_discord_links (player_uuid, code, code_expires_at) "
                        + "VALUES (?, ?, NOW() + INTERVAL ? SECOND) "
                        + "ON DUPLICATE KEY UPDATE code = VALUES(code), code_expires_at = VALUES(code_expires_at)",
                player.toString(), code, validity.toSeconds());
    }

    /** Returns false if the account was not linked to a Discord account. */
    public boolean unlink(UUID player) {
        return db.update("UPDATE bc_discord_links SET discord_id = NULL, linked_at = NULL "
                + "WHERE player_uuid = ? AND discord_id IS NOT NULL", player.toString()) > 0;
    }

    private static Link map(ResultSet row) throws SQLException {
        return new Link(Optional.ofNullable(row.getString("discord_id")), Optional.ofNullable(row.getString("code")));
    }
}
