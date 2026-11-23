package me.blockcraft.replay;

import me.blockcraft.common.db.Database;
import me.blockcraft.replay.storage.ReplayStore;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

final class ReplayRepository {

    /** {@code favourite} is whether the viewer it was read for keeps it. */
    record Entry(UUID id, String game, String map, Instant startedAt, int durationTicks,
                 String endReason, long bytes, String minecraft, boolean favourite) { }

    /** A viewing request; {@code origin} is the group the viewer left for it, empty when unknown. */
    record Request(UUID replay, Optional<String> origin) { }

    /** Every query reads for one viewer; their favourites are theirs to watch, as the rounds they played are. */
    private static final String SELECT = "SELECT r.*, f.player_uuid IS NOT NULL AS favourite FROM bc_replays r "
            + "LEFT JOIN bc_replay_favourites f ON f.replay_uuid=r.replay_uuid AND f.player_uuid=? WHERE ";
    private static final String ACCESS = "(f.player_uuid IS NOT NULL OR EXISTS (SELECT 1 FROM bc_replay_players p "
            + "WHERE p.replay_uuid=r.replay_uuid AND p.player_uuid=?))";

    private final Database db;

    ReplayRepository(Database db) {
        this.db = db;
    }

    void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_replays (replay_uuid CHAR(36) PRIMARY KEY, "
                + "game VARCHAR(64) NOT NULL, map_name VARCHAR(255) NOT NULL, started_at TIMESTAMP(3) NOT NULL, "
                + "duration_ticks INT NOT NULL, end_reason VARCHAR(64) NOT NULL, bytes BIGINT NOT NULL, "
                + "minecraft VARCHAR(32) NOT NULL, INDEX replays_started (started_at))");
        db.execute("CREATE TABLE IF NOT EXISTS bc_replay_players (replay_uuid CHAR(36) NOT NULL, player_uuid CHAR(36) NOT NULL, "
                + "PRIMARY KEY (replay_uuid, player_uuid), INDEX replay_player (player_uuid), "
                + "FOREIGN KEY (replay_uuid) REFERENCES bc_replays(replay_uuid) ON DELETE CASCADE)");
        db.execute("CREATE TABLE IF NOT EXISTS bc_replay_requests (player_uuid CHAR(36) PRIMARY KEY, replay_uuid CHAR(36) NOT NULL, "
                + "request_uuid CHAR(36) NOT NULL, "
                + "expires_at TIMESTAMP NOT NULL, FOREIGN KEY (replay_uuid) REFERENCES bc_replays(replay_uuid) ON DELETE CASCADE)");
        db.execute("ALTER TABLE bc_replay_requests ADD COLUMN IF NOT EXISTS origin VARCHAR(64) NULL");
        db.execute("CREATE TABLE IF NOT EXISTS bc_replay_favourites (player_uuid CHAR(36) NOT NULL, replay_uuid CHAR(36) NOT NULL, "
                + "PRIMARY KEY (player_uuid, replay_uuid), INDEX favourite_replay (replay_uuid), "
                + "FOREIGN KEY (replay_uuid) REFERENCES bc_replays(replay_uuid) ON DELETE CASCADE)");
    }

    void save(ReplayStore.Saved saved) {
        var header = saved.header();
        db.transaction(connection -> {
            db.update(connection, "INSERT INTO bc_replays (replay_uuid,game,map_name,started_at,duration_ticks,end_reason,bytes,minecraft) "
                            + "VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE bytes=VALUES(bytes)",
                    header.id().toString(), header.game(), header.map(), Timestamp.from(header.startedAt()),
                    saved.durationTicks(), saved.endReason(), saved.bytes(), header.minecraft());
            for (var participant : header.participants()) {
                db.update(connection, "INSERT IGNORE INTO bc_replay_players (replay_uuid,player_uuid) VALUES (?,?)",
                        header.id().toString(), participant.id().toString());
            }
            return null;
        });
    }

    /** The viewer's favourites, all of them, then the latest {@code limit} other recordings they may watch. */
    List<Entry> history(UUID viewer, boolean staff, int limit) {
        var favourites = db.queryList(SELECT + "f.player_uuid IS NOT NULL ORDER BY r.started_at DESC",
                ReplayRepository::entry, viewer.toString());
        var latest = db.queryList(SELECT + "f.player_uuid IS NULL AND " + (staff ? "1=1" : ACCESS)
                        + " ORDER BY r.started_at DESC LIMIT ?", ReplayRepository::entry,
                staff ? new Object[]{viewer.toString(), limit} : new Object[]{viewer.toString(), viewer.toString(), limit});
        return Stream.concat(favourites.stream(), latest.stream()).toList();
    }

    Optional<Entry> accessible(UUID id, UUID viewer, boolean staff) {
        return db.queryOne(SELECT + "r.replay_uuid=? AND " + (staff ? "1=1" : ACCESS), ReplayRepository::entry,
                staff ? new Object[]{viewer.toString(), id.toString()}
                        : new Object[]{viewer.toString(), id.toString(), viewer.toString()});
    }

    /** Keeps or drops the viewer's favourite and reports whether anyone still keeps the recording. */
    boolean favourite(UUID viewer, UUID replay, boolean kept) {
        db.update(kept ? "INSERT IGNORE INTO bc_replay_favourites (player_uuid,replay_uuid) VALUES (?,?)"
                : "DELETE FROM bc_replay_favourites WHERE player_uuid=? AND replay_uuid=?", viewer.toString(), replay.toString());
        return db.queryOne("SELECT 1 FROM bc_replay_favourites WHERE replay_uuid=? LIMIT 1", row -> true, replay.toString())
                .isPresent();
    }

    /** The recordings at least one player keeps. */
    Set<UUID> favourites() {
        return Set.copyOf(db.queryList("SELECT DISTINCT replay_uuid FROM bc_replay_favourites",
                row -> UUID.fromString(row.getString("replay_uuid"))));
    }

    void request(UUID viewer, UUID replay, UUID request, String origin) {
        db.update("INSERT INTO bc_replay_requests (player_uuid,replay_uuid,request_uuid,origin,expires_at) "
                        + "VALUES (?,?,?,?,DATE_ADD(NOW(),INTERVAL 60 SECOND)) "
                        + "ON DUPLICATE KEY UPDATE replay_uuid=VALUES(replay_uuid),request_uuid=VALUES(request_uuid),"
                        + "origin=VALUES(origin),expires_at=VALUES(expires_at)",
                viewer.toString(), replay.toString(), request.toString(), origin);
    }

    void cancelRequest(UUID viewer, UUID request) {
        db.update("DELETE FROM bc_replay_requests WHERE player_uuid=? AND request_uuid=?",
                viewer.toString(), request.toString());
    }

    Optional<Request> takeRequest(UUID viewer) {
        return db.transaction(connection -> {
            try (var statement = connection.prepareStatement("SELECT replay_uuid,origin,expires_at > NOW() AS valid "
                    + "FROM bc_replay_requests WHERE player_uuid=? FOR UPDATE")) {
                statement.setString(1, viewer.toString());
                Optional<Request> result;
                try (var rows = statement.executeQuery()) {
                    result = rows.next() && rows.getBoolean("valid")
                            ? Optional.of(new Request(UUID.fromString(rows.getString("replay_uuid")),
                            Optional.ofNullable(rows.getString("origin")))) : Optional.empty();
                }
                db.update(connection, "DELETE FROM bc_replay_requests WHERE player_uuid=?", viewer.toString());
                return result;
            }
        });
    }

    void remove(UUID replay) {
        db.update("DELETE FROM bc_replays WHERE replay_uuid=?", replay.toString());
    }

    private static Entry entry(ResultSet row) throws SQLException {
        return new Entry(UUID.fromString(row.getString("replay_uuid")), row.getString("game"), row.getString("map_name"),
                row.getTimestamp("started_at").toInstant(), row.getInt("duration_ticks"), row.getString("end_reason"),
                row.getLong("bytes"), row.getString("minecraft"), row.getBoolean("favourite"));
    }
}
