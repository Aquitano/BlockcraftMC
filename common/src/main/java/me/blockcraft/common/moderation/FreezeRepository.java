package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.player.PlayerReference;

import java.util.Optional;
import java.util.UUID;

/** Players frozen by staff; the flag follows them across servers until a staff member lifts it. */
public final class FreezeRepository {

    private final Database db;

    public FreezeRepository(Database db) {
        this.db = db;
    }

    public Optional<PlayerReference> issuer(UUID player) {
        return db.queryOne("SELECT issuer, issuer_uuid FROM bc_frozen WHERE player_uuid = ?",
                row -> PunishmentRepository.reference(row, "issuer", "issuer_uuid"), player.toString());
    }

    public void freeze(UUID player, PlayerReference issuer) {
        db.update("INSERT INTO bc_frozen (player_uuid, issuer, issuer_uuid) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE issuer = VALUES(issuer), issuer_uuid = VALUES(issuer_uuid), since = NOW()",
                player.toString(), issuer.name(), PunishmentRepository.uuidOf(issuer));
    }

    public void unfreeze(UUID player) {
        db.update("DELETE FROM bc_frozen WHERE player_uuid = ?", player.toString());
    }
}
