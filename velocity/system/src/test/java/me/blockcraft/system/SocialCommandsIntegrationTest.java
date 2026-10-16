package me.blockcraft.system;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.ignore.IgnoreRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.player.FriendCommand;
import me.blockcraft.system.command.player.MessageCommand;
import me.blockcraft.system.friends.FriendManager;
import me.blockcraft.system.friends.FriendRepository;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.moderation.MuteGate;
import me.blockcraft.system.network.ServerDirectory;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Uses the same isolated-schema MariaDB setup as common's integration tests. */
public class SocialCommandsIntegrationTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String url;
    private String password;
    private String schema;
    private Database db;
    private FakeProxy proxy;
    private PlayerVisibility visibility;
    private PlayerChoices choices;
    private PlayerRepository players;
    private FriendRepository repository;
    private FriendManager friends;
    private Messages messages;
    private CommandDispatcher<CommandSource> commands;

    @Before
    public void createSchemaAndCommands() throws Exception {
        String configuredPort = System.getenv("BLOCKCRAFT_TEST_DB_PORT");
        Assume.assumeTrue("Set BLOCKCRAFT_TEST_DB_PORT to run MariaDB integration tests", configuredPort != null);
        int port = Integer.parseInt(configuredPort);
        password = System.getenv().getOrDefault("BLOCKCRAFT_TEST_DB_PASSWORD", "blockcraft-test");
        url = "jdbc:mariadb://127.0.0.1:" + port + "/";
        schema = "bc_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "root", password); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema);
        }
        db = Database.connect(new DatabaseConfig("127.0.0.1", port, schema, "root", password, 4, Duration.ofSeconds(30)), "social-test");
        db.migrate();
        proxy = new FakeProxy();
        visibility = new PlayerVisibility(proxy.server, null, null, null);
        choices = new PlayerChoices(null, null);
        players = new PlayerRepository(db);
        repository = new FriendRepository(db);
        repository.createTable();
        var data = folder.newFolder().toPath();
        var config = SystemConfig.load(data.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        messages = Messages.load(data, () -> getClass().getResourceAsStream("/messages.yml"));
        var lookup = new PlayerLookup(proxy.server, visibility, players, messages);
        var ignores = new IgnoreList(new IgnoreRepository(db), null);
        var directory = new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), counts -> { });
        friends = new FriendManager(proxy.server, lookup, visibility, choices, ignores, directory, null, repository, null, config, messages);
        var support = new CommandSupport(visibility, messages);
        var muteGate = new MuteGate(new PunishmentRepository(db), null, messages, null);
        var messaging = new MessageCommand(support, proxy.server, visibility, lookup, choices, ignores, new AfkPlayers(), muteGate, messages);
        commands = new CommandDispatcher<>();
        commands.getRoot().addChild(messaging.msg().getNode());
        commands.getRoot().addChild(messaging.reply().getNode());
        commands.getRoot().addChild(new FriendCommand(support, friends, messages).create().getNode());
    }

    @After
    public void dropSchema() throws Exception {
        if (db != null) {
            db.close();
        }
        if (schema != null) {
            try (var connection = DriverManager.getConnection(url, "root", password); var sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    @Test
    public void repliesRespectVanishEvenWithMessageBypassAndResumeAfterUnvanishing() throws Exception {
        var sender = join("Sender", SystemPermissions.MSG_BYPASS);
        var target = join("Target");
        commands.execute("msg Target hello", sender);
        int receivedBeforeVanish = proxy.received(target).size();

        visibility.setVanished(target, true);
        commands.execute("r hidden", sender);

        assertEquals(messages.get("msg.offline"), proxy.received(sender).getLast());
        assertEquals(receivedBeforeVanish, proxy.received(target).size());

        var staff = join("Staff", SystemPermissions.SEE_VANISHED);
        commands.execute("msg Target staff", staff);
        commands.execute("r still-visible", staff);
        assertEquals(receivedBeforeVanish + 2, proxy.received(target).size());

        visibility.setVanished(target, false);
        commands.execute("r back", sender);
        assertEquals(receivedBeforeVanish + 3, proxy.received(target).size());
    }

    @Test
    public void suggestionsIncludeOfflineRelationshipsAndOnlyRelevantVisibleNames() throws Exception {
        var viewer = join("Viewer");
        var nicked = join("Account");
        var requester = join("RequestAccount");
        var offlineFriend = join("OfflineFriend");
        var offlineRequest = join("OfflineRequest");
        join("Unrelated");
        nick(nicked, "Nickname");
        nick(requester, "RequestNick");
        befriend(viewer, nicked);
        befriend(viewer, offlineFriend);
        repository.request(requester.getUniqueId(), viewer.getUniqueId());
        repository.request(offlineRequest.getUniqueId(), viewer.getUniqueId());
        proxy.leave(offlineFriend);
        proxy.leave(offlineRequest);

        assertEquals(List.of("OfflineRequest", "RequestNick"), suggest(viewer, "friend accept "));
        assertEquals(List.of("RequestNick"), suggest(viewer, "friend deny REQ"));
        assertEquals(List.of("Nickname", "OfflineFriend"), suggest(viewer, "friend remove "));
        assertEquals(List.of("Nickname"), suggest(viewer, "friend jump "));
        assertFalse(suggest(viewer, "friend add ").contains("Account"));

        visibility.setVanished(nicked, true);
        assertTrue(suggest(viewer, "friend jump ").isEmpty());
        commands.execute("friend jump Account", viewer);
        assertLast(viewer, "friends.offline", "Account");
    }

    @Test
    public void storedAccountActionsDoNotRevealTheirOnlineNickname() throws Exception {
        var viewer = join("Viewer");
        var target = join("Account");
        nick(target, "SecretNick");
        repository.request(target.getUniqueId(), viewer.getUniqueId());

        commands.execute("friend accept Account", viewer);
        assertTrue(repository.areFriends(viewer.getUniqueId(), target.getUniqueId()));
        assertLast(viewer, "friends.accepted", "Account");
        assertTrue(proxy.received(target).isEmpty());

        commands.execute("friend remove Account", viewer);
        assertFalse(repository.areFriends(viewer.getUniqueId(), target.getUniqueId()));
        assertLast(viewer, "friends.removed", "Account");
        assertTrue(proxy.received(target).isEmpty());
    }

    @Test
    public void hiddenAndOfflineRequestsRemainManageableByTheirStoredNames() throws Exception {
        var viewer = join("Viewer");
        var hidden = join("Hidden");
        var offline = join("Offline");
        visibility.setVanished(hidden, true);
        proxy.leave(offline);
        repository.request(hidden.getUniqueId(), viewer.getUniqueId());
        repository.request(offline.getUniqueId(), viewer.getUniqueId());

        commands.execute("friend accept Hidden", viewer);
        assertTrue(repository.areFriends(viewer.getUniqueId(), hidden.getUniqueId()));
        assertLast(viewer, "friends.accepted", "Hidden");
        assertTrue(proxy.received(hidden).isEmpty());
        commands.execute("friend remove Hidden", viewer);
        assertFalse(repository.areFriends(viewer.getUniqueId(), hidden.getUniqueId()));

        commands.execute("friend deny Offline", viewer);
        assertTrue(repository.requestsTo(viewer.getUniqueId(), 10).isEmpty());
        assertLast(viewer, "friends.denied", "Offline");
        repository.request(offline.getUniqueId(), viewer.getUniqueId());
        commands.execute("friend accept Offline", viewer);
        assertTrue(repository.areFriends(viewer.getUniqueId(), offline.getUniqueId()));
        commands.execute("friend remove Offline", viewer);
        assertFalse(repository.areFriends(viewer.getUniqueId(), offline.getUniqueId()));
    }

    @Test
    public void nicknamesAndPrivilegedAccountNamesResolveConsistently() throws Exception {
        var viewer = join("Viewer");
        var staff = join("Staff", SystemPermissions.SEE_NICKS, SystemPermissions.SEE_VANISHED);
        var target = join("Account");
        nick(target, "Nickname");

        commands.execute("friend add Account", viewer);
        assertLast(viewer, "friends.unknown", "Account");
        assertFalse(repository.hasOpenRequest(viewer.getUniqueId(), target.getUniqueId(), 10));
        commands.execute("friend add Nickname", viewer);
        assertTrue(repository.hasOpenRequest(viewer.getUniqueId(), target.getUniqueId(), 10));

        repository.request(target.getUniqueId(), viewer.getUniqueId());
        commands.execute("friend accept Nickname", viewer);
        assertLast(viewer, "friends.accepted", "Nickname");
        assertEquals(List.of("Nickname"), friends.friendNames(viewer));

        visibility.setVanished(target, true);
        repository.request(target.getUniqueId(), staff.getUniqueId());
        assertEquals(List.of("Account"), suggest(staff, "friend accept "));
        commands.execute("friend accept Account", staff);
        assertLast(staff, "friends.accepted", "Account");
        assertEquals(List.of("Account"), friends.friendNames(staff));
        assertEquals(List.of("Account"), suggest(staff, "friend jump "));
    }

    private Player join(String name, String... additional) {
        var permissions = new java.util.ArrayList<>(List.of(additional));
        permissions.addAll(List.of(SystemPermissions.MSG, SystemPermissions.FRIENDS));
        var player = proxy.join(name, permissions.toArray(String[]::new));
        players.upsert(player.getUniqueId(), name);
        choices.set(player, PlayerSetting.PRIVATE_MESSAGES, PlayerSetting.value(true));
        choices.set(player, PlayerSetting.SOUNDS, PlayerSetting.value(false));
        return player;
    }

    private void nick(Player player, String name) {
        visibility.setNick(player, Optional.of(new Nick(name, "Notch", "default")));
    }

    private void befriend(Player first, Player second) {
        repository.request(first.getUniqueId(), second.getUniqueId());
        assertTrue(repository.accept(first.getUniqueId(), second.getUniqueId(), 10));
    }

    private List<String> suggest(Player player, String input) throws Exception {
        return commands.getCompletionSuggestions(commands.parse(input, player)).get(10, TimeUnit.SECONDS)
                .getList().stream().map(Suggestion::getText).toList();
    }

    private void assertLast(Player player, String key, String name) {
        assertEquals(messages.get(key, Placeholder.unparsed("player", name)), proxy.received(player).getLast());
    }
}
