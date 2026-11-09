package me.blockcraft.story.game;

import me.blockcraft.common.db.Database;

import java.util.Optional;
import java.util.UUID;

/**
 * The party's decision at the end of a mission, kept for every member so that the next mission of the campaign can
 * build on it, whoever of the party plays on.
 */
public final class ChoiceRepository {

    private final Database db;

    public ChoiceRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_story_choices (player_uuid CHAR(36) NOT NULL, mission VARCHAR(32) NOT NULL, "
                + "choice VARCHAR(32) NOT NULL, decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, mission))");
    }

    public void record(UUID player, String mission, Choice choice) {
        db.update("INSERT INTO bc_story_choices (player_uuid, mission, choice) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE choice = VALUES(choice), decided_at = CURRENT_TIMESTAMP", player.toString(), mission, choice.name());
    }

    public Optional<Choice> find(UUID player, String mission) {
        return db.queryOne("SELECT choice FROM bc_story_choices WHERE player_uuid = ? AND mission = ?",
                row -> Choice.valueOf(row.getString("choice")), player.toString(), mission);
    }
}
