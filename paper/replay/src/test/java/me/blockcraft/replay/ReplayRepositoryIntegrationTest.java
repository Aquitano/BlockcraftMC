package me.blockcraft.replay;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;
import me.blockcraft.replay.storage.ReplayStore;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayRepositoryIntegrationTest {

    @Rule public TemporaryFolder files = new TemporaryFolder();

    private String url;
    private String password;
    private String schema;
    private Database db;
    private ReplayRepository repository;

    @Before
    public void createSchema() throws SQLException {
        String configuredPort = System.getenv("BLOCKCRAFT_TEST_DB_PORT");
        Assume.assumeTrue("Set BLOCKCRAFT_TEST_DB_PORT to run MariaDB integration tests", configuredPort != null);
        int port = Integer.parseInt(configuredPort);
        password = System.getenv().getOrDefault("BLOCKCRAFT_TEST_DB_PASSWORD", "blockcraft-test");
        url = "jdbc:mariadb://127.0.0.1:" + port + "/";
        String name = "bc_test_replay_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "root", password); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + name);
        }
        schema = name;
        db = Database.connect(new DatabaseConfig("127.0.0.1", port, schema, "root", password, 4, Duration.ofSeconds(30)),
                "replay-test");
        repository = new ReplayRepository(db);
        repository.initialize();
    }

    @After
    public void dropSchema() throws SQLException {
        if (db != null) { db.close(); }
        if (schema != null) {
            try (var connection = DriverManager.getConnection(url, "root", password); var sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    @Test
    public void historyRestrictsParticipantsAndRepeatedPublicationKeepsOneReplay() {
        var owner = UUID.randomUUID();
        var opponent = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        var first = recording(Instant.parse("2026-10-29T12:00:00Z"), owner, opponent);
        var second = recording(Instant.parse("2026-10-29T12:01:00Z"), stranger);
        repository.save(first);
        repository.save(new ReplayStore.Saved(first.header(), 2048, first.durationTicks(), first.endReason()));
        repository.save(second);

        assertEquals(List.of(first.header().id()), repository.history(owner, false, 10).stream().map(ReplayRepository.Entry::id).toList());
        assertTrue(repository.accessible(first.header().id(), opponent, false).isPresent());
        assertTrue(repository.accessible(first.header().id(), stranger, false).isEmpty());
        assertTrue(repository.accessible(first.header().id(), stranger, true).isPresent());
        assertEquals(List.of(second.header().id(), first.header().id()),
                repository.history(owner, true, 10).stream().map(ReplayRepository.Entry::id).toList());
        assertEquals(List.of(second.header().id()),
                repository.history(owner, true, 1).stream().map(ReplayRepository.Entry::id).toList());
        assertEquals(2048, repository.accessible(first.header().id(), owner, false).orElseThrow().bytes());
        assertEquals(1, count("SELECT COUNT(*) FROM bc_replays WHERE replay_uuid=?", first.header().id().toString()));
        assertEquals(2, count("SELECT COUNT(*) FROM bc_replay_players WHERE replay_uuid=?", first.header().id().toString()));
    }

    @Test
    public void transferRequestsReplacePreviousChoiceAndOnlyOneConcurrentConsumerWins() throws Exception {
        var viewer = UUID.randomUUID();
        var first = recording(Instant.now(), viewer);
        var second = recording(Instant.now(), viewer);
        repository.save(first);
        repository.save(second);
        repository.request(viewer, first.header().id(), UUID.randomUUID());
        var replaced = UUID.randomUUID();
        repository.request(viewer, second.header().id(), replaced);
        repository.request(viewer, second.header().id(), UUID.randomUUID());
        repository.cancelRequest(viewer, replaced);
        assertEquals(1, count("SELECT COUNT(*) FROM bc_replay_requests WHERE player_uuid=?", viewer.toString()));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Optional<UUID>> consume = () -> repository.takeRequest(viewer);
            var results = executor.invokeAll(List.of(consume, consume, consume, consume));
            int consumed = 0;
            for (var result : results) {
                var request = result.get();
                if (request.isPresent()) {
                    assertEquals(second.header().id(), request.orElseThrow());
                    consumed++;
                }
            }
            assertEquals(1, consumed);
        }
        assertTrue(repository.takeRequest(viewer).isEmpty());
    }

    @Test
    public void expiredRequestsAreConsumedAndReplayRemovalDeletesParticipantsAndRequests() {
        var viewer = UUID.randomUUID();
        var saved = recording(Instant.now(), viewer);
        var replay = saved.header().id();
        repository.save(saved);
        repository.request(viewer, replay, UUID.randomUUID());
        db.update("UPDATE bc_replay_requests SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE player_uuid=?", viewer.toString());

        assertTrue(repository.takeRequest(viewer).isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM bc_replay_requests WHERE player_uuid=?", viewer.toString()));

        repository.request(viewer, replay, UUID.randomUUID());
        repository.remove(replay);

        assertFalse(repository.accessible(replay, viewer, true).isPresent());
        assertTrue(repository.history(viewer, false, 10).isEmpty());
        assertTrue(repository.takeRequest(viewer).isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM bc_replay_players WHERE replay_uuid=?", replay.toString()));
        assertEquals(0, count("SELECT COUNT(*) FROM bc_replay_requests WHERE replay_uuid=?", replay.toString()));
    }

    private int count(String sql, Object... parameters) {
        return db.queryOne(sql, row -> row.getInt(1), parameters).orElseThrow();
    }

    @Test
    public void retentionRecoversOnlyPublishedArchivesAndRemovesExpiredFilesAndHistory() throws Exception {
        var directory = files.getRoot().toPath();
        var limits = new ReplayLimits(2, 1200, 2, 2, 1, 1, 16, 4, 100,
                1024 * 1024, 2 * 1024 * 1024, 1, 256, 4096);
        var config = new ReplayConfig("replay", directory, 10, 8 * 1024 * 1024, 0,
                Duration.ofDays(7), Duration.ofMinutes(15), limits);
        var owner = UUID.randomUUID();
        var header = recording(Instant.now(), owner).header();
        var now = Instant.now();
        var logger = Logger.getLogger("replay-test");
        try (var store = new ReplayStore(directory, limits, config.maxStoredBytes(), 0)) {
            store.begin(header).finish(200, "DURATION_LIMIT").get(5, TimeUnit.SECONDS);
            ReplayRetention.clean(store, repository, config, Set.of(), now, logger);
            assertTrue(repository.history(owner, false, 10).isEmpty());
            store.markReady(header.id());
            ReplayRetention.clean(store, repository, config, Set.of(), now, logger);
            assertTrue(repository.accessible(header.id(), owner, false).isPresent());
            ReplayRetention.clean(store, repository, config, Set.of(header.id()), now.plus(Duration.ofDays(8)), logger);
            assertTrue(Files.exists(store.path(header.id())));
            ReplayRetention.clean(store, repository, config, Set.of(), now.plus(Duration.ofDays(8)), logger);
            assertFalse(Files.exists(store.path(header.id())));
            assertFalse(store.isReady(header.id()));
            assertTrue(repository.history(owner, false, 10).isEmpty());
        }
    }

    private static ReplayStore.Saved recording(Instant started, UUID... players) {
        var participants = java.util.Arrays.stream(players)
                .map(id -> new ReplayHeader.Participant(id, "Player", "", "")).toList();
        var header = new ReplayHeader(UUID.randomUUID(), "duell", "duell-1", "26.3", "test", "Arena", started, participants);
        return new ReplayStore.Saved(header, 1024, 200, "MATCH_END");
    }
}
