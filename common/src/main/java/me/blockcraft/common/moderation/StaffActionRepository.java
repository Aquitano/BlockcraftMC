package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.player.PlayerReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code bc_staff_actions}: written by every Paper server, read by the proxy's {@code /stafflog}. */
public final class StaffActionRepository {

    public record Page(List<StaffAction> entries, int total) {
    }

    private static final String COLUMNS = "id, issuer_uuid, issuer_name, action, target_uuid, target_name, detail, server, created_at";
    private static final String INVOLVING = " FROM bc_staff_actions WHERE issuer_uuid = ? OR target_uuid = ?";

    private final Database db;

    public StaffActionRepository(Database db) {
        this.db = db;
    }

    public void record(PlayerReference issuer, String action, Optional<PlayerReference> target, Optional<String> detail, String server) {
        db.update("INSERT INTO bc_staff_actions (issuer_uuid, issuer_name, action, target_uuid, target_name, detail, server) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                PunishmentRepository.uuidOf(issuer), issuer.name(), action,
                target.map(PunishmentRepository::uuidOf).orElse(null), target.map(PlayerReference::name).orElse(null),
                detail.orElse(null), server);
    }

    /** Actions by or on the player, newest first; {@code page} counts from zero. */
    public Page involving(UUID player, int page, int size) {
        String id = player.toString();
        int total = db.queryOne("SELECT COUNT(*)" + INVOLVING, row -> row.getInt(1), id, id).orElse(0);
        var entries = db.queryList("SELECT " + COLUMNS + INVOLVING + " ORDER BY id DESC LIMIT ? OFFSET ?",
                StaffActionRepository::map, id, id, size, (long) page * size);
        return new Page(entries, total);
    }

    private static StaffAction map(ResultSet row) throws SQLException {
        var targetId = Optional.ofNullable(row.getString("target_uuid")).map(UUID::fromString);
        var target = Optional.ofNullable(row.getString("target_name")).map(name -> new PlayerReference(name, targetId));
        return new StaffAction(row.getLong("id"), PunishmentRepository.reference(row, "issuer_name", "issuer_uuid"),
                row.getString("action"), target, Optional.ofNullable(row.getString("detail")), row.getString("server"),
                row.getTimestamp("created_at").toInstant());
    }
}
