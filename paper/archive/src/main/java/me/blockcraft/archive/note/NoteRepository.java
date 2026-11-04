package me.blockcraft.archive.note;

import me.blockcraft.api.world.Point;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.player.PlayerReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The notes staff left in the exhibits, one row each in {@code bc_archive_notes}, which only this plugin uses. */
public final class NoteRepository {

    private static final String COLUMNS = "id, exhibit, x, y, z, yaw, author_uuid, author_name, text, created_at";

    private final Database db;

    public NoteRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_archive_notes ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "exhibit VARCHAR(64) NOT NULL, "
                + "x DOUBLE NOT NULL, "
                + "y DOUBLE NOT NULL, "
                + "z DOUBLE NOT NULL, "
                + "yaw FLOAT NOT NULL, "
                + "author_uuid CHAR(36) NULL, "
                + "author_name VARCHAR(16) NOT NULL, "
                + "text VARCHAR(512) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_bc_archive_notes_exhibit (exhibit))");
    }

    public Note add(String exhibit, Point at, PlayerReference author, String text) {
        return db.queryOne("INSERT INTO bc_archive_notes (exhibit, x, y, z, yaw, author_uuid, author_name, text) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS, NoteRepository::note,
                exhibit, at.x(), at.y(), at.z(), at.yaw(), author.uuid().map(UUID::toString).orElse(null), author.name(), text)
                .orElseThrow(() -> new IllegalStateException("The note was not stored"));
    }

    public List<Note> list(String exhibit) {
        return db.queryList("SELECT " + COLUMNS + " FROM bc_archive_notes WHERE exhibit = ? ORDER BY created_at, id",
                NoteRepository::note, exhibit);
    }

    public boolean delete(long id) {
        return db.update("DELETE FROM bc_archive_notes WHERE id = ?", id) > 0;
    }

    private static Note note(ResultSet row) throws SQLException {
        var uuid = Optional.ofNullable(row.getString("author_uuid")).map(UUID::fromString);
        return new Note(row.getLong("id"), row.getString("exhibit"),
                new Point(row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"), 0.0f),
                new PlayerReference(row.getString("author_name"), uuid), row.getString("text"),
                row.getTimestamp("created_at").toInstant());
    }
}
