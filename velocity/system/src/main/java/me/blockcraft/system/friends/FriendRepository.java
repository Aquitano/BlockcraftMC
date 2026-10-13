package me.blockcraft.system.friends;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * {@code bc_friends}: one row per pair, the two uuids ordered so that the pair can only exist once, the player who
 * asked in {@code requester}. A request is a {@code PENDING} row that turns into {@code ACCEPTED} or is deleted;
 * the name shown next to a friend comes from {@code bc_players}, so a rename follows by itself.
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
                + "first_player CHAR(36) NOT NULL, "
                + "second_player CHAR(36) NOT NULL, "
                + "requester CHAR(36) NOT NULL, "
                + "status VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (first_player, second_player), "
                + "INDEX idx_bc_friends_second (second_player))");
    }

    public List<Friend> friendsOf(UUID player) {
        return db.queryList("SELECT p.uuid, p.name, f.created_at FROM bc_friends f "
                        + "JOIN bc_players p ON p.uuid = IF(f.first_player = ?, f.second_player, f.first_player) "
                        + "WHERE f.status = '" + ACCEPTED + "' AND (f.first_player = ? OR f.second_player = ?) ORDER BY p.name",
                FriendRepository::map, player.toString(), player.toString(), player.toString());
    }

    public List<UUID> friendUuidsOf(UUID player) {
        return db.queryList("SELECT IF(first_player = ?, second_player, first_player) AS other FROM bc_friends "
                        + "WHERE status = '" + ACCEPTED + "' AND (first_player = ? OR second_player = ?)",
                row -> UUID.fromString(row.getString("other")), player.toString(), player.toString(), player.toString());
    }

    /** The open requests waiting for {@code player} to answer, expired ones left out. */
    public List<Friend> requestsTo(UUID player, int validMinutes) {
        return db.queryList("SELECT p.uuid, p.name, f.created_at FROM bc_friends f JOIN bc_players p ON p.uuid = f.requester "
                        + "WHERE f.status = '" + PENDING + "' AND f.requester <> ? "
                        + "AND (f.first_player = ? OR f.second_player = ?) "
                        + "AND TIMESTAMPDIFF(MINUTE, f.created_at, NOW()) < ? ORDER BY f.created_at",
                FriendRepository::map, player.toString(), player.toString(), player.toString(), validMinutes);
    }

    public int countFriends(UUID player) {
        return db.queryOne("SELECT COUNT(*) FROM bc_friends WHERE status = '" + ACCEPTED + "' "
                        + "AND (first_player = ? OR second_player = ?)",
                row -> row.getInt(1), player.toString(), player.toString()).orElse(0);
    }

    public boolean areFriends(UUID player, UUID other) {
        var pair = Pair.of(player, other);
        return db.queryOne("SELECT 1 FROM bc_friends WHERE status = '" + ACCEPTED + "' "
                        + "AND first_player = ? AND second_player = ?",
                row -> true, pair.first(), pair.second()).isPresent();
    }

    /** Whether either side already has an open request to the other. */
    public boolean hasOpenRequest(UUID player, UUID other, int validMinutes) {
        var pair = Pair.of(player, other);
        return db.queryOne("SELECT 1 FROM bc_friends WHERE status = '" + PENDING + "' "
                        + "AND first_player = ? AND second_player = ? "
                        + "AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) < ?",
                row -> true, pair.first(), pair.second(), validMinutes).isPresent();
    }

    /** Replaces whatever stale row the pair had, so an expired request can be sent again. */
    public void request(UUID requester, UUID target) {
        var pair = Pair.of(requester, target);
        db.update("INSERT INTO bc_friends (first_player, second_player, requester, status) VALUES (?, ?, ?, '" + PENDING + "') "
                        + "ON DUPLICATE KEY UPDATE requester = VALUES(requester), status = VALUES(status), created_at = NOW()",
                pair.first(), pair.second(), requester.toString());
    }

    /** False when there was no open request from {@code requester}. */
    public boolean accept(UUID requester, UUID target, int validMinutes) {
        var pair = Pair.of(requester, target);
        return db.update("UPDATE bc_friends SET status = '" + ACCEPTED + "', created_at = NOW() "
                        + "WHERE first_player = ? AND second_player = ? AND requester = ? AND status = '" + PENDING + "' "
                        + "AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) < ?",
                pair.first(), pair.second(), requester.toString(), validMinutes) > 0;
    }

    public boolean deleteRequest(UUID requester, UUID target) {
        var pair = Pair.of(requester, target);
        return db.update("DELETE FROM bc_friends WHERE first_player = ? AND second_player = ? AND requester = ? "
                        + "AND status = '" + PENDING + "'",
                pair.first(), pair.second(), requester.toString()) > 0;
    }

    public boolean remove(UUID player, UUID other) {
        var pair = Pair.of(player, other);
        return db.update("DELETE FROM bc_friends WHERE status = '" + ACCEPTED + "' "
                        + "AND first_player = ? AND second_player = ?",
                pair.first(), pair.second()) > 0;
    }

    public void deleteExpiredRequests(int validMinutes) {
        db.update("DELETE FROM bc_friends WHERE status = '" + PENDING + "' AND TIMESTAMPDIFF(MINUTE, created_at, NOW()) >= ?",
                validMinutes);
    }

    private static Friend map(ResultSet row) throws SQLException {
        return new Friend(UUID.fromString(row.getString("uuid")), row.getString("name"), row.getTimestamp("created_at").toInstant());
    }

    /** The two sides of a friendship in the order the primary key stores them, so a pair has one row whoever asked. */
    private record Pair(String first, String second) {

        static Pair of(UUID player, UUID other) {
            var one = player.toString();
            var two = other.toString();
            return one.compareTo(two) <= 0 ? new Pair(one, two) : new Pair(two, one);
        }
    }
}
