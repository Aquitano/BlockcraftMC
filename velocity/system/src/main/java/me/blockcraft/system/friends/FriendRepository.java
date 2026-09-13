package me.blockcraft.system.friends;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * {@code bc_friends}: one row per pair, the player who asked first in {@code requester}. A request is a
 * {@code PENDING} row that turns into {@code ACCEPTED} or is deleted; the name shown next to a friend comes
 * from {@code bc_players}, so a rename follows by itself.
 */
public final class FriendRepository {

    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";

    private final Database db;

    public FriendRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_friends ("
                + "requester CHAR(36) NOT NULL, "
                + "target CHAR(36) NOT NULL, "
                + "status VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (requester, target), "
                + "INDEX idx_bc_friends_target (target))");
    }

    public List<Friend> friendsOf(UUID player) {
        return db.queryList("SELECT p.uuid, p.name FROM bc_friends f "
                        + "JOIN bc_players p ON p.uuid = IF(f.requester = ?, f.target, f.requester) "
                        + "WHERE f.status = '" + ACCEPTED + "' AND (f.requester = ? OR f.target = ?) ORDER BY p.name",
                FriendRepository::map, player.toString(), player.toString(), player.toString());
    }

    public List<UUID> friendUuidsOf(UUID player) {
        return db.queryList("SELECT IF(requester = ?, target, requester) AS other FROM bc_friends "
                        + "WHERE status = '" + ACCEPTED + "' AND (requester = ? OR target = ?)",
                row -> UUID.fromString(row.getString("other")), player.toString(), player.toString(), player.toString());
    }

    /** The open requests waiting for {@code player} to answer, expired ones left out. */
    public List<Friend> requestsTo(UUID player, int validMinutes) {
        return db.queryList("SELECT p.uuid, p.name FROM bc_friends f JOIN bc_players p ON p.uuid = f.requester "
                        + "WHERE f.status = '" + PENDING + "' AND f.target = ? "
                        + "AND TIMESTAMPDIFF(MINUTE, f.created_at, NOW()) < ? ORDER BY f.created_at",
                FriendRepository::map, player.toString(), validMinutes);
    }

    public int countFriends(UUID player) {
        return db.queryOne("SELECT COUNT(*) FROM bc_friends WHERE status = '" + ACCEPTED + "' AND (requester = ? OR target = ?)",
                row -> row.getInt(1), player.toString(), player.toString()).orElse(0);
    }

    public boolean areFriends(UUID player, UUID other) {
        return db.queryOne("SELECT 1 FROM bc_friends WHERE status = '" + ACCEPTED + "' AND "
                        + "((requester = ? AND target = ?) OR (requester = ? AND target = ?))",
                row -> true, player.toString(), other.toString(), other.toString(), player.toString()).isPresent();
    }

    /** Whether either side already has an open request to the other. */
    public boolean hasOpenRequest(UUID player, UUID other, int validMinutes) {
        return db.queryOne("SELECT 1 FROM bc_friends WHERE status = '" + PENDING + "' "
                        + "AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) < ? "
                        + "AND ((requester = ? AND target = ?) OR (requester = ? AND target = ?))",
                row -> true, validMinutes, player.toString(), other.toString(), other.toString(), player.toString()).isPresent();
    }

    /** Replaces whatever stale row the pair had, so an expired request can be sent again. */
    public void request(UUID requester, UUID target) {
        db.update("DELETE FROM bc_friends WHERE (requester = ? AND target = ?) OR (requester = ? AND target = ?)",
                requester.toString(), target.toString(), target.toString(), requester.toString());
        db.update("INSERT INTO bc_friends (requester, target, status) VALUES (?, ?, '" + PENDING + "')",
                requester.toString(), target.toString());
    }

    /** False when there was no open request from {@code requester}. */
    public boolean accept(UUID requester, UUID target, int validMinutes) {
        return db.update("UPDATE bc_friends SET status = '" + ACCEPTED + "', created_at = NOW() "
                        + "WHERE requester = ? AND target = ? AND status = '" + PENDING + "' "
                        + "AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) < ?",
                requester.toString(), target.toString(), validMinutes) > 0;
    }

    public boolean deleteRequest(UUID requester, UUID target) {
        return db.update("DELETE FROM bc_friends WHERE requester = ? AND target = ? AND status = '" + PENDING + "'",
                requester.toString(), target.toString()) > 0;
    }

    public boolean remove(UUID player, UUID other) {
        return db.update("DELETE FROM bc_friends WHERE status = '" + ACCEPTED + "' AND "
                        + "((requester = ? AND target = ?) OR (requester = ? AND target = ?))",
                player.toString(), other.toString(), other.toString(), player.toString()) > 0;
    }

    public void deleteExpiredRequests(int validMinutes) {
        db.update("DELETE FROM bc_friends WHERE status = '" + PENDING + "' AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) >= ?",
                validMinutes);
    }

    private static Friend map(ResultSet row) throws SQLException {
        return new Friend(UUID.fromString(row.getString("uuid")), row.getString("name"));
    }
}
