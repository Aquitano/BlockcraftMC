package me.blockcraft.story.campaign;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.blockcraft.common.db.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The campaigns of one mission in {@code bc_story_campaigns}, with who played in each. */
public final class Campaigns {

    private static final Gson GSON = new Gson();
    private static final TypeToken<Map<String, String>> FACTS = new TypeToken<>() { };

    private static final String SELECT = "SELECT c.campaign_id, c.revision, c.facts, c.ending FROM bc_story_campaigns c "
            + "JOIN bc_story_campaign_members m ON m.campaign_id = c.campaign_id WHERE m.player_uuid = ? AND c.mission = ? ";
    private static final String LATEST = "ORDER BY c.saved_at DESC, c.revision DESC LIMIT 1";

    private final Database db;
    private final String mission;

    public Campaigns(Database db, String mission) {
        this.db = db;
        this.mission = mission;
    }

    /**
     * The campaign {@code player} last saved without reaching an ending, which the party picks up again; none once they
     * have finished a campaign since, as a run they left behind for another is over for them.
     */
    public Optional<Campaign> unfinished(UUID player) {
        return latest(player).filter(campaign -> campaign.ending().isEmpty());
    }

    /** The campaign {@code player} last finished, for what it decided. */
    public Optional<Campaign> finished(UUID player) {
        return db.queryOne(SELECT + "AND c.ending IS NOT NULL " + LATEST, Campaigns::campaign, player.toString(), mission);
    }

    private Optional<Campaign> latest(UUID player) {
        return db.queryOne("SELECT c.campaign_id, c.revision, c.facts, c.ending FROM bc_story_campaigns c "
                        + "JOIN bc_story_current_campaigns selected ON selected.campaign_id = c.campaign_id "
                        + "WHERE selected.player_uuid = ? AND selected.mission = ?",
                Campaigns::campaign, player.toString(), mission);
    }

    /**
     * Writes {@code next} over {@code saved} as {@code writer}, with every member, in one transaction. True once
     * {@code next} is stored, also when an earlier try by the same writer already stored it and only its answer was
     * lost; false when the campaign has moved on past {@code saved}, by another writer or another save.
     */
    public boolean save(Campaign saved, Campaign next, Collection<UUID> members, UUID writer) {
        var facts = GSON.toJson(next.facts());
        var ending = next.ending().orElse(null);
        int written = db.transaction(connection -> {
            boolean joining = saved.revision() == 0 || !writtenBy(connection, saved.id(), writer);
            int rows = saved.revision() == 0
                    ? insert(connection, next, facts, ending, writer)
                    : db.update(connection, "UPDATE bc_story_campaigns SET revision = ?, facts = ?, ending = ?, writer = ?, "
                            + "saved_at = CURRENT_TIMESTAMP(6) WHERE campaign_id = ? AND revision = ?",
                            next.revision(), facts, ending, writer.toString(), next.id().toString(), saved.revision());
            if (rows == 1) {
                for (var member : members) {
                    int added = db.update(connection, "INSERT IGNORE INTO bc_story_campaign_members (campaign_id, player_uuid) VALUES (?, ?)",
                            next.id().toString(), member.toString());
                    if (joining || added == 1) {
                        db.update(connection, "INSERT INTO bc_story_current_campaigns (player_uuid, mission, campaign_id) VALUES (?, ?, ?) "
                                        + "ON DUPLICATE KEY UPDATE campaign_id = VALUES(campaign_id)",
                                member.toString(), mission, next.id().toString());
                    }
                }
            }
            return rows;
        });
        return written == 1 || stored(next.id(), writer).filter(next::equals).isPresent();
    }

    private static boolean writtenBy(Connection connection, UUID campaign, UUID writer) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT writer FROM bc_story_campaigns WHERE campaign_id = ? FOR UPDATE")) {
            statement.setString(1, campaign.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() && rows.getString("writer").equals(writer.toString());
            }
        }
    }

    public void rejoin(UUID campaign, UUID member, UUID writer) {
        db.update("INSERT INTO bc_story_current_campaigns (player_uuid, mission, campaign_id) "
                        + "SELECT m.player_uuid, c.mission, c.campaign_id FROM bc_story_campaigns c "
                        + "JOIN bc_story_campaign_members m ON m.campaign_id = c.campaign_id "
                        + "WHERE c.campaign_id = ? AND m.player_uuid = ? AND c.writer = ? "
                        + "ON DUPLICATE KEY UPDATE campaign_id = VALUES(campaign_id)",
                campaign.toString(), member.toString(), writer.toString());
    }

    /** A first save: 0 rows when the campaign is already there, as it is after a retry. */
    private int insert(Connection connection, Campaign next, String facts, String ending, UUID writer) throws SQLException {
        try {
            return db.update(connection, "INSERT INTO bc_story_campaigns (campaign_id, mission, revision, facts, ending, writer) VALUES (?, ?, ?, ?, ?, ?)",
                    next.id().toString(), mission, next.revision(), facts, ending, writer.toString());
        } catch (SQLIntegrityConstraintViolationException exists) {
            return 0;
        }
    }

    /** The campaign as {@code writer} last stored it; empty when someone else wrote it last. */
    Optional<Campaign> stored(UUID id, UUID writer) {
        return db.queryOne("SELECT campaign_id, revision, facts, ending FROM bc_story_campaigns WHERE campaign_id = ? AND writer = ?",
                Campaigns::campaign, id.toString(), writer.toString());
    }

    private static Campaign campaign(ResultSet row) throws SQLException {
        return new Campaign(UUID.fromString(row.getString("campaign_id")), row.getInt("revision"),
                GSON.fromJson(row.getString("facts"), FACTS), Optional.ofNullable(row.getString("ending")));
    }
}
