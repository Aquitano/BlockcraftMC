package me.blockcraft.common.db;

import me.blockcraft.common.elo.EloFormula;
import me.blockcraft.common.duel.DuelPairRepository;
import me.blockcraft.common.elo.EloRepository;
import me.blockcraft.common.elo.Season;
import me.blockcraft.common.moderation.PlayerAddressRepository;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.moderation.WarnRepository;
import me.blockcraft.common.moderation.WatchRepository;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.common.round.RoundRecap;
import me.blockcraft.common.round.RoundRecapRepository;
import me.blockcraft.common.shop.Shop;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Runs against the MariaDB named by {@code BLOCKCRAFT_TEST_DB_PORT} and is skipped without it. Each test creates
 * and drops its own {@code bc_test_*} schema.
 */
public class DatabaseIntegrationTest {

    private static final String HOST = "127.0.0.1";
    private static final String USER = "root";
    private static final int APPLIED = SharedSchema.MIGRATIONS.size();
    private static final int NEXT = SharedSchema.MIGRATIONS.getLast().version() + 1;

    private int port;
    private String password;
    private String url;
    private String schema;
    private Database db;

    @Before
    public void createSchema() throws SQLException {
        String configuredPort = System.getenv("BLOCKCRAFT_TEST_DB_PORT");
        Assume.assumeTrue("Set BLOCKCRAFT_TEST_DB_PORT to run MariaDB integration tests", configuredPort != null);
        port = Integer.parseInt(configuredPort);
        password = System.getenv().getOrDefault("BLOCKCRAFT_TEST_DB_PASSWORD", "blockcraft-test");
        url = "jdbc:mariadb://" + HOST + ":" + port + "/";
        schema = "bc_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, USER, password); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema);
        }
        db = connect("test");
    }

    @After
    public void dropSchema() throws SQLException {
        if (db != null) {
            db.close();
        }
        if (schema != null) {
            try (var connection = DriverManager.getConnection(url, USER, password); var sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    private Database connect(String poolName) {
        return Database.connect(new DatabaseConfig(HOST, port, schema, USER, password, 4, Duration.ofSeconds(30)), poolName);
    }

    private int count(String sql, Object... params) {
        return db.queryOne(sql, row -> row.getInt(1), params).orElseThrow();
    }

    @Test
    public void aDuelPairRewardsBothPlayersOnceWhenItsDrawsInARowReachTheCount() {
        db.migrate();
        var pairs = new DuelPairRepository(db);
        pairs.createTable();
        var one = UUID.randomUUID();
        var two = UUID.randomUUID();

        assertFalse(pairs.met(one, two));
        assertFalse(pairs.finish(one, two, true, 2, "tag.spiegel"));
        assertTrue(pairs.met(two, one));
        assertTrue(pairs.finish(two, one, true, 2, "tag.spiegel"));
        assertFalse(pairs.finish(one, two, true, 2, "tag.spiegel"));
        assertEquals(2, count("SELECT COUNT(*) FROM bc_unlocks WHERE item = 'tag.spiegel'"));
        assertFalse(pairs.finish(one, two, false, 2, "tag.other"));
        assertFalse(pairs.finish(one, two, true, 2, "tag.other"));
        assertEquals(0, count("SELECT COUNT(*) FROM bc_unlocks WHERE item = 'tag.other'"));
    }

    @Test
    public void simultaneousNewcomersKeepDistinctJoinNumbers() throws Exception {
        db.migrate();
        var players = new PlayerRepository(db);
        var ids = java.util.stream.IntStream.range(0, 16).mapToObj(index -> UUID.randomUUID()).toList();
        ids.forEach(id -> players.upsert(id, "Newcomer"));
        var numbers = new java.util.HashSet<Integer>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var joins = ids.stream().<Callable<Integer>>map(id -> () -> players.assignJoinNumber(id).orElseThrow()).toList();
            for (var join : pool.invokeAll(joins)) {
                numbers.add(join.get());
            }
        }
        assertEquals(ids.size(), numbers.size());
        for (var id : ids) {
            assertEquals(players.assignJoinNumber(id), players.assignJoinNumber(id));
            assertTrue(players.verifiedFirstSeen(id));
        }
    }

    @Test
    public void concurrentGreetingsPayAtMostTheDailyCapAndNeverPayTwice() throws Exception {
        db.migrate();
        var greeter = UUID.randomUUID();
        new PlayerRepository(db).upsert(greeter, "Greeter");
        var greetings = new me.blockcraft.common.welcome.GreetingRepository(db);
        var newcomers = java.util.stream.IntStream.range(0, 8).mapToObj(index -> UUID.randomUUID()).toList();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = newcomers.stream().<Callable<Void>>map(newcomer -> () -> {
                greetings.reward(greeter, "Shown", "Greeter", newcomer, 10, 2);
                greetings.reward(greeter, "Shown", "Greeter", newcomer, 10, 2);
                return null;
            }).toList();
            for (var request : pool.invokeAll(requests)) { request.get(); }
        }
        assertEquals(8, greetings.greetedBy(greeter));
        assertEquals(20, count("SELECT COINS FROM VCOINS WHERE UUID = ?", greeter.toString()));
        assertEquals(List.of("Shown"), greetings.greeters(newcomers.getFirst()));
    }

    @Test
    public void unlockRewardsPayOnceAndRollbackIfCreditFails() throws Exception {
        db.migrate();
        var rewards = new me.blockcraft.common.shop.UnlockRewards(db);
        var player = UUID.randomUUID();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var claims = java.util.stream.IntStream.range(0, 8).<Callable<Boolean>>mapToObj(index ->
                    () -> rewards.grant(player, "Player", "calendar.advent.2026.1", 25)).toList();
            int paid = 0;
            for (var claim : pool.invokeAll(claims)) { if (claim.get()) { paid++; } }
            assertEquals(1, paid);
        }
        assertEquals(25, count("SELECT COINS FROM VCOINS WHERE UUID = ?", player.toString()));
        db.execute("CREATE TRIGGER reject_reward BEFORE UPDATE ON VCOINS FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test credit failure'");
        assertThrows(DatabaseException.class, () -> rewards.grant(player, "Player", "calendar.advent.2026.2", 25));
        assertEquals(0, count("SELECT COUNT(*) FROM bc_unlocks WHERE item = 'calendar.advent.2026.2'"));
        assertEquals(25, count("SELECT COINS FROM VCOINS WHERE UUID = ?", player.toString()));
    }

    @Test
    public void ogEligibilityIsCapturedOnceFromLegacyCoinsAndStats() throws Exception {
        var coinsPlayer = UUID.randomUUID();
        var statsPlayer = UUID.randomUUID();
        db.execute("CREATE TABLE VCOINS (UUID VARCHAR(36) PRIMARY KEY, NAME VARCHAR(16), COINS INT NOT NULL DEFAULT 0)");
        db.update("INSERT INTO VCOINS (UUID, NAME) VALUES (?, 'OldCoins')", coinsPlayer.toString());
        db.execute("CREATE TABLE qsg_stats (UUID VARCHAR(36) PRIMARY KEY)");
        db.update("INSERT INTO qsg_stats (UUID) VALUES (?)", statsPlayer.toString());
        db.migrate();
        assertEquals(2, count("SELECT COUNT(*) FROM bc_unlocks WHERE item = 'tag.og'"));
        db.update("INSERT INTO VCOINS (UUID, NAME) VALUES (?, 'NewPlayer')", UUID.randomUUID().toString());
        try (var restarted = connect("restarted")) { restarted.migrate(); }
        assertEquals(2, count("SELECT COUNT(*) FROM bc_unlocks WHERE item = 'tag.og'"));
    }

    @Test
    public void upgradesLegacyReportsWithoutLosingRowsAndRunsOnce() {
        db.execute("CREATE TABLE Reports (ID INT AUTO_INCREMENT PRIMARY KEY, Reportername VARCHAR(100), Reportedname VARCHAR(100), "
                + "Grund VARCHAR(1000), Server VARCHAR(100), Status VARCHAR(100))");
        db.update("INSERT INTO Reports (Reportername, Reportedname, Grund, Server, Status) VALUES ('Reporter', 'Target', 'Reason', 'lobby', 'Offen')");
        db.migrate();
        db.migrate();
        var report = new ReportRepository(db).all().getFirst();
        assertEquals("Reason", report.reason());
        assertTrue(report.reportedId().isEmpty());
        assertEquals(APPLIED, count("SELECT COUNT(*) FROM bc_schema_migrations"));
    }

    @Test
    public void concurrentStartupSerializesMigrations() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var starts = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 4; i++) {
                starts.add(() -> {
                    try (var other = connect("parallel")) {
                        other.migrate();
                    }
                    return null;
                });
            }
            for (var start : pool.invokeAll(starts)) {
                start.get();
            }
        }
        assertEquals(APPLIED, count("SELECT COUNT(*) FROM bc_schema_migrations"));
    }

    @Test
    public void failedMigrationCanResumeAndAppliedChecksumsCannotChange() throws Exception {
        db.migrate();
        try (var connection = DriverManager.getConnection(url + schema, USER, password)) {
            String probe = "CREATE TABLE IF NOT EXISTS bc_probe (id INT PRIMARY KEY)";
            assertThrows(SQLException.class, () -> Migrations.apply(connection, List.of(new Migration(NEXT, List.of(probe, "INVALID SQL")))));
            assertEquals(0, count("SELECT COUNT(*) FROM bc_schema_migrations WHERE version = ?", NEXT));
            Migrations.apply(connection, List.of(new Migration(NEXT, List.of(probe))));
            assertThrows(SQLException.class, () -> Migrations.apply(connection, List.of(new Migration(NEXT, List.of("SELECT 1")))));
        }
    }

    @Test
    public void concurrentPurchasesChargeOnceAndCannotOverspend() throws Exception {
        db.migrate();
        var player = UUID.randomUUID();
        db.update("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, 'Buyer', 100)", player.toString());
        var shop = new Shop(db);
        int bought = 0;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var clicks = new ArrayList<Callable<Shop.Purchase>>();
            for (int i = 0; i < 16; i++) {
                clicks.add(() -> shop.buy(player, "Buyer", "test.item", 60));
            }
            for (var click : pool.invokeAll(clicks)) {
                if (click.get().bought()) {
                    bought++;
                }
            }
        }
        assertEquals(1, bought);
        assertEquals(Shop.Outcome.NOT_ENOUGH_COINS, shop.buy(player, "Buyer", "test.other", 60).outcome());
        assertEquals(40, count("SELECT COINS FROM VCOINS WHERE UUID = ?", player.toString()));
        assertEquals(1, count("SELECT COUNT(*) FROM bc_unlocks"));
    }

    @Test
    public void paymentFailureRollsBackUnlock() {
        db.migrate();
        var player = UUID.randomUUID();
        db.update("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, 'Buyer', 100)", player.toString());
        db.execute("CREATE TRIGGER fail_payment BEFORE UPDATE ON VCOINS FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected failure'");
        assertThrows(DatabaseException.class, () -> new Shop(db).buy(player, "Buyer", "test.item", 60));
        assertEquals(0, count("SELECT COUNT(*) FROM bc_unlocks"));
        assertEquals(100, count("SELECT COINS FROM VCOINS"));
    }

    @Test
    public void reportsKeepIdentityAndClaimsAcrossNameChanges() {
        db.migrate();
        var reports = new ReportRepository(db);
        var reporter = PlayerReference.known(UUID.randomUUID(), "Reporter");
        var target = PlayerReference.known(UUID.randomUUID(), "Target");
        assertTrue(reports.insert(reporter, target, "Reason", "lobby"));
        assertFalse(reports.insert(reporter, PlayerReference.known(target.uuid().orElseThrow(), "Renamed"), "Other", "lobby"));
        assertEquals(1, reports.countByReported(target.uuid().orElseThrow(), "Renamed"));

        var report = reports.all().getFirst();
        assertEquals(target.uuid(), report.target().uuid());

        var staff = UUID.randomUUID();
        assertTrue(reports.claim(report.id(), staff, "Staff"));
        assertFalse(reports.claim(report.id(), UUID.randomUUID(), "Other"));
        assertTrue(reports.byId(report.id()).orElseThrow().isClaimedBy(staff, "NewStaffName"));
        reports.release(report.id(), staff, "NewStaffName");
        assertTrue(reports.byId(report.id()).orElseThrow().claimedBy().isEmpty());
    }

    @Test
    public void recapIsTakenOnceAndOnlyWhileFresh() {
        db.migrate();
        var recaps = new RoundRecapRepository(db);
        var player = UUID.randomUUID();
        var recap = new RoundRecap("quicksg", true, "Winner", 3, 1, 2, 40, 80);
        recaps.save(Map.of(player, recap));
        assertEquals(recap, recaps.take(player, Duration.ofMinutes(10)).orElseThrow());
        assertTrue(recaps.take(player, Duration.ofMinutes(10)).isEmpty());

        recaps.save(Map.of(player, recap));
        db.update("UPDATE bc_round_recaps SET ended_at = NOW() - INTERVAL 11 MINUTE WHERE player_uuid = ?", player.toString());
        assertTrue(recaps.take(player, Duration.ofMinutes(10)).isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM bc_round_recaps"));
    }

    @Test
    public void aSettledRoundMovesBothRatingsWithinTheSeasonOnly() {
        db.migrate();
        var elo = new EloRepository(db, new EloFormula(1000, 32));
        var season = Season.of(LocalDate.of(2026, 9, 18));
        var winner = UUID.randomUUID();
        var loser = UUID.randomUUID();
        var match = elo.settle(winner, "Winner", loser, "Loser", season);
        assertEquals(16, match.delta());
        assertEquals(1016, elo.get(winner, season).rating());
        assertEquals(984, elo.byName("Loser", season).orElseThrow().rating());
        assertEquals(1, elo.get(loser, season).losses());
        assertEquals("Winner", elo.top(season, 10).getFirst().name());
        assertEquals(1000, elo.get(winner, Season.of(LocalDate.of(2026, 10, 1))).rating());
    }

    @Test
    public void accountsSharingAnAddressAreLinkedWithinRetentionOnly() {
        db.migrate();
        var players = new PlayerRepository(db);
        var addresses = new PlayerAddressRepository(db);
        var banned = UUID.randomUUID();
        var alt = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        players.upsert(banned, "Banned");
        players.upsert(alt, "Alt");
        players.upsert(stranger, "Stranger");
        byte[] home = new byte[32];
        byte[] elsewhere = new byte[32];
        elsewhere[0] = 1;
        addresses.record(banned, home);
        addresses.record(alt, home);
        addresses.record(alt, home);
        addresses.record(stranger, elsewhere);
        new PunishmentRepository(db).ban(banned, "Hacking", PlayerReference.named("Console"), Optional.empty());
        var retention = Duration.ofDays(90);

        var alts = addresses.alts(alt, retention);
        assertEquals(List.of(banned), alts.stream().map(PlayerAddressRepository.Alt::uuid).toList());
        assertTrue(alts.getFirst().banned());
        assertFalse(alts.getFirst().muted());
        assertTrue(addresses.sharedWithBannedAccount(alt, home, retention));
        assertFalse(addresses.sharedWithBannedAccount(stranger, elsewhere, retention));
        assertEquals(1, new WatchRepository(db).of(alt, "Alt", Duration.ofDays(30), retention).bannedAlts());

        db.update("UPDATE bc_player_addresses SET last_seen = NOW() - INTERVAL 91 DAY WHERE player_uuid = ?", banned.toString());
        assertTrue(addresses.alts(alt, retention).isEmpty());
        assertEquals(1, addresses.purge(retention));
    }

    @Test
    public void warnsKeepTheirIssuerAndLegacyWarnsHaveNone() {
        db.migrate();
        var warns = new WarnRepository(db);
        var player = UUID.randomUUID();
        db.update("INSERT INTO bc_warns (player_uuid, reason) VALUES (?, NULL)", player.toString());
        var staff = PlayerReference.known(UUID.randomUUID(), "Staff");
        assertEquals(2, warns.add(player, Optional.of("Spam"), staff));
        var listed = warns.list(player);
        assertTrue(listed.getFirst().issuer().isEmpty());
        assertEquals(Optional.of(staff), listed.getLast().issuer());
    }
}
