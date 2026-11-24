package me.blockcraft.story.campaign;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class CampaignsIntegrationTest {

    private String url;
    private String password;
    private String schema;
    private Database db;
    private Campaigns campaigns;
    private final UUID writer = UUID.randomUUID();

    @Before
    public void createSchema() throws SQLException {
        String configuredPort = System.getenv("BLOCKCRAFT_TEST_DB_PORT");
        Assume.assumeTrue("Set BLOCKCRAFT_TEST_DB_PORT to run MariaDB integration tests", configuredPort != null);
        int port = Integer.parseInt(configuredPort);
        password = System.getenv().getOrDefault("BLOCKCRAFT_TEST_DB_PASSWORD", "blockcraft-test");
        url = "jdbc:mariadb://127.0.0.1:" + port + "/";
        String name = "bc_test_story_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "root", password); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + name);
        }
        schema = name;
        db = Database.connect(new DatabaseConfig("127.0.0.1", port, schema, "root", password, 4, Duration.ofSeconds(30)), "story-test");
        db.migrate();
        campaigns = new Campaigns(db, "tessar");
    }

    @After
    public void dropSchema() throws SQLException {
        if (db != null) db.close();
        if (schema != null) {
            try (var connection = DriverManager.getConnection(url, "root", password); var sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    @Test
    public void aRetriedSaveThatWentThroughCountsAndAStaleSaveIsRefused() {
        var member = UUID.randomUUID();
        var fresh = Campaign.fresh();
        var briefing = fresh.next(Map.of("stage", "briefing"), Optional.empty());

        assertTrue(campaigns.save(fresh, briefing, List.of(member), writer));
        assertTrue("a retry after a lost answer", campaigns.save(fresh, briefing, List.of(member), writer));
        assertFalse("a different first save", campaigns.save(fresh, fresh.next(Map.of("stage", "relay"), Optional.empty()), List.of(member), writer));

        var relay = briefing.next(Map.of("stage", "relay"), Optional.empty());
        assertTrue(campaigns.save(briefing, relay, List.of(member), writer));
        assertTrue("a retry after a lost answer", campaigns.save(briefing, relay, List.of(member), writer));
        assertFalse("a save over a revision that has moved on", campaigns.save(briefing, briefing.next(Map.of("stage", "defense"), Optional.empty()), List.of(member), writer));
        assertEquals(Optional.of(relay), campaigns.stored(fresh.id(), writer));
    }

    @Test
    public void anotherServerSavingTheSameIsNotTakenForOwn() {
        var member = UUID.randomUUID();
        var fresh = Campaign.fresh();
        var briefing = fresh.next(Map.of("stage", "briefing"), Optional.empty());
        assertTrue(campaigns.save(fresh, briefing, List.of(member), writer));

        var relay = briefing.next(Map.of("stage", "relay"), Optional.empty());
        assertTrue(campaigns.save(briefing, relay, List.of(member), UUID.randomUUID()));
        assertFalse(campaigns.save(briefing, relay, List.of(member), writer));
    }

    @Test
    public void aMemberPicksUpTheCampaignTheyLeftUnfinished() {
        var member = UUID.randomUUID();
        var joinedLater = UUID.randomUUID();
        var done = Campaign.fresh();
        campaigns.save(done, done.next(Map.of("stage", "done", "decision", "fabrik"), Optional.of("fabrik")), List.of(member), writer);
        var open = Campaign.fresh();
        var briefing = open.next(Map.of("stage", "briefing"), Optional.empty());
        campaigns.save(open, briefing, List.of(member), writer);
        var defense = briefing.next(Map.of("stage", "defense"), Optional.empty());
        campaigns.save(briefing, defense, List.of(member, joinedLater), writer);

        assertEquals(Optional.of(defense), campaigns.unfinished(member));
        assertEquals(Optional.of(defense), campaigns.unfinished(joinedLater));
        assertEquals(Optional.of("fabrik"), campaigns.finished(member).map(campaign -> campaign.facts().get("decision")));
        assertEquals(Optional.empty(), campaigns.unfinished(UUID.randomUUID()));
    }

    @Test
    public void aCampaignLeftBehindIsOverOnceTheMemberFinishesAnother() {
        var member = UUID.randomUUID();
        var remaining = UUID.randomUUID();
        var left = Campaign.fresh();
        var relay = left.next(Map.of("stage", "relay"), Optional.empty());
        campaigns.save(left, relay, List.of(member, remaining), writer);
        var other = Campaign.fresh();
        campaigns.save(other, other.next(Map.of("stage", "done"), Optional.of("gefangene")), List.of(member), writer);

        var defense = relay.next(Map.of("stage", "defense"), Optional.empty());
        assertTrue(campaigns.save(relay, defense, List.of(member, remaining), writer));

        assertEquals(Optional.empty(), campaigns.unfinished(member));
        assertEquals(Optional.of(defense), campaigns.unfinished(remaining));
    }

    @Test
    public void returningToAnEarlierCampaignOnAnotherServerSelectsItAgain() {
        var member = UUID.randomUUID();
        var first = Campaign.fresh();
        var relay = first.next(Map.of("stage", "relay"), Optional.empty());
        campaigns.save(first, relay, List.of(member), writer);
        var other = Campaign.fresh();
        campaigns.save(other, other.next(Map.of("stage", "done"), Optional.of("gefangene")), List.of(member), writer);

        var defense = relay.next(Map.of("stage", "defense"), Optional.empty());
        assertTrue(campaigns.save(relay, defense, List.of(member), UUID.randomUUID()));
        assertEquals(Optional.of(defense), campaigns.unfinished(member));
    }

    @Test
    public void rejoiningTheOriginalServerSelectsOnlyTheReturningMember() {
        var member = UUID.randomUUID();
        var remaining = UUID.randomUUID();
        var first = Campaign.fresh();
        var relay = first.next(Map.of("stage", "relay"), Optional.empty());
        campaigns.save(first, relay, List.of(member, remaining), writer);
        var other = Campaign.fresh();
        campaigns.save(other, other.next(Map.of("stage", "done"), Optional.of("gefangene")), List.of(member, remaining), writer);

        campaigns.rejoin(first.id(), member, UUID.randomUUID());
        assertEquals(Optional.empty(), campaigns.unfinished(member));
        campaigns.rejoin(first.id(), member, writer);
        assertEquals(Optional.of(relay), campaigns.unfinished(member));
        assertEquals(Optional.empty(), campaigns.unfinished(remaining));
        assertEquals(Optional.of(relay), campaigns.stored(first.id(), writer));
    }

    @Test
    public void aFailedRejoinSelectionIsRetriedAtTheNextCheckpoint() throws Exception {
        var member = UUID.randomUUID();
        var first = Campaign.fresh();
        try (var saves = new Saves(campaigns, first, LoggerFactory.getLogger(getClass()))) {
            assertEquals(Saves.Outcome.SAVED, saves.save(Map.of("stage", "relay"), Optional.empty(), List.of(member)).get(5, TimeUnit.SECONDS));
            var other = Campaign.fresh();
            campaigns.save(other, other.next(Map.of("stage", "done"), Optional.of("gefangene")), List.of(member), writer);
            db.execute("CREATE TRIGGER reject_selection BEFORE INSERT ON bc_story_current_campaigns "
                    + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test selection failure'");
            assertThrows(ExecutionException.class, () -> saves.rejoin(member).get(5, TimeUnit.SECONDS));
            db.execute("DROP TRIGGER reject_selection");

            assertEquals(Saves.Outcome.SAVED, saves.save(Map.of("stage", "defense"), Optional.empty(), List.of(member)).get(5, TimeUnit.SECONDS));
            assertEquals(Optional.of("defense"), campaigns.unfinished(member).map(Campaign::stage));
        }
    }
}
