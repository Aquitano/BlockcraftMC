package me.blockcraft.common.anticheat;

import me.blockcraft.common.db.Database;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AnticheatEvidenceRepository {

    public record Entry(long id, AnticheatEvidence evidence, boolean replayPublished) { }

    private final Database db;

    public AnticheatEvidenceRepository(Database db) {
        this.db = db;
    }

    public void append(AnticheatEvidence evidence) {
        String name = db.queryOne("SELECT name FROM bc_players WHERE uuid = ?", row -> row.getString(1),
                evidence.player().toString()).orElse(evidence.name());
        db.update("INSERT INTO bc_anticheat_evidence (player_uuid, player_name, server, recorded_at, check_name, "
                        + "description, violations, verbose, replay_uuid, replay_tick) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                evidence.player().toString(), name, evidence.server(), Timestamp.from(evidence.recordedAt()),
                evidence.check(), evidence.description(), evidence.violations(), evidence.verbose(),
                evidence.replay().map(replay -> replay.id().toString()).orElse(null),
                evidence.replay().map(AnticheatEvidence.Replay::tick).orElse(null));
    }

    public void prune(int days, int maximum) {
        db.update("DELETE FROM bc_anticheat_evidence WHERE recorded_at < CURRENT_TIMESTAMP - INTERVAL ? DAY", days);
        db.queryOne("SELECT id FROM bc_anticheat_evidence ORDER BY id DESC LIMIT 1 OFFSET ?",
                row -> row.getLong(1), maximum - 1).ifPresent(oldest ->
                db.update("DELETE FROM bc_anticheat_evidence WHERE id < ?", oldest));
    }

    public List<Entry> latest(Optional<UUID> player, int days, int limit) {
        var rows = db.queryList("SELECT * FROM bc_anticheat_evidence WHERE recorded_at >= CURRENT_TIMESTAMP - INTERVAL ? DAY"
                        + (player.isPresent() ? " AND player_uuid = ?" : "") + " ORDER BY id DESC LIMIT ?",
                row -> {
                    int replayTick = row.getInt("replay_tick");
                    var replay = Optional.ofNullable(row.getString("replay_uuid"))
                            .map(id -> new AnticheatEvidence.Replay(UUID.fromString(id), replayTick));
                    return new Entry(row.getLong("id"), new AnticheatEvidence(UUID.fromString(row.getString("player_uuid")),
                            row.getString("player_name"), row.getString("server"), row.getTimestamp("recorded_at").toInstant(),
                            row.getString("check_name"), row.getString("description"), row.getString("violations"),
                            row.getString("verbose"), replay), false);
                }, player.isPresent() ? new Object[]{days, player.get().toString(), limit} : new Object[]{days, limit});
        if (rows.stream().noneMatch(entry -> entry.evidence().replay().isPresent())) {
            return rows;
        }
        boolean tablesExist = db.queryOne("SELECT COUNT(*) = 2 FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('bc_replays', 'bc_replay_players')", row -> row.getBoolean(1)).orElse(false);
        if (!tablesExist) {
            return rows;
        }
        var published = new HashMap<UUID, Map<AnticheatEvidence.Replay, Boolean>>();
        return rows.stream().map(entry -> {
            var evidence = entry.evidence();
            boolean available = evidence.replay().map(replay -> published.computeIfAbsent(evidence.player(), ignored -> new HashMap<>())
                    .computeIfAbsent(replay, ignored -> db.queryOne("SELECT 1 FROM bc_replays r JOIN bc_replay_players p "
                                    + "ON p.replay_uuid = r.replay_uuid WHERE r.replay_uuid = ? AND p.player_uuid = ? AND r.duration_ticks > ?",
                            row -> true, replay.id().toString(), evidence.player().toString(), replay.tick()).orElse(false))).orElse(false);
            return new Entry(entry.id(), evidence, available);
        }).toList();
    }
}
