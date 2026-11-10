package me.blockcraft.system.network;

import me.blockcraft.system.FakeProxy;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.LobbyConnector;

import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.duel.DuelRules;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.*;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.duel.DuelChallenges;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import me.blockcraft.system.ranked.RankedQueue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class DuelChallengesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final FakeProxy proxy = new FakeProxy();
    private final TestClock clock = new TestClock();
    private Player sender;
    private Player target;
    private Player stranger;
    private ServerDirectory directory;
    private DuelChallenges challenges;

    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Before
    public void setup() throws Exception {
        sender = proxy.join("Sender");
        target = proxy.join("Target");
        stranger = proxy.join("Stranger");
        proxy.connect(sender, "duell-1");
        proxy.connect(sender, "lobby-1");
        proxy.connect(target, "lobby-1");
        proxy.connect(stranger, "lobby-1");
        directory = new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), clock::instant, counts -> { });
        refresh();
        var data = folder.getRoot().toPath();
        var config = SystemConfig.load(data.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        var messages = Messages.load(data, () -> getClass().getResourceAsStream("/messages.yml"));
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        var choices = new PlayerChoices(null, null);
        choices.set(target, PlayerSetting.DUEL_INVITES, "on");
        var ignores = new IgnoreList(null, null);
        var parties = new PartyManager(proxy.server, visibility, choices, ignores, directory, messages);
        var lobby = new LobbyConnector(directory, parties, config, messages, Optional.empty());
        var ranked = new RankedQueue(proxy.server, directory, lobby, parties, Optional.empty(), null,
                config.ranked().queue(), messages, null, clock);
        challenges = new DuelChallenges(proxy.server, directory, lobby, parties, ranked, visibility, choices,
                ignores, Optional.empty(), messages, clock, config);
    }

    private void refresh() {
        directory.update(List.of(record("duell-1", "duell", 2), record("lobby-1", "lobby", 100)));
    }

    private ServerRecord record(String name, String group, int capacity) {
        return new ServerRecord(new ServerIdentity(name, group, "127.0.0.1", 25566),
                new ServerStatus(ServerState.LOBBY, 0, capacity, 20, 5), 0);
    }

    private ProxyMessage.DuelSetup draft() {
        challenges.request(sender, "Target");
        assertEquals("duell-1", proxy.requested(sender).getLast().getServerInfo().getName());
        var old = sender.getCurrentServer().orElseThrow();
        var connection = proxy.connect(sender, "duell-1");
        directory.onServerPostConnect(new ServerPostConnectEvent(sender, old.getServer()));
        challenges.onConnect(new ServerPostConnectEvent(sender, old.getServer()));
        return (ProxyMessage.DuelSetup) ProxyMessageCodec.decode(proxy.sentTo(connection).getLast(), ProxyMessage.ToBackend.class).orElseThrow();
    }

    @Test
    public void onlyTheInvitedPlayerCanAcceptAndDuplicateAcceptsDoNotTransferAgain() {
        var setup = draft();
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(stranger.getUniqueId(), "duell-1"));
        challenges.invite(sender, new ProxyMessage.DuelInvitation(setup.ticket(), new DuelRules("sumo", 1, true, true, 3), "Sumo"));
        challenges.answer(stranger, "accept", setup.ticket().toString());
        assertTrue(proxy.requested(stranger).isEmpty());
        assertTrue(proxy.requested(target).isEmpty());
        challenges.answer(target, "accept", UUID.randomUUID().toString());
        assertTrue(proxy.requested(target).isEmpty());
        challenges.answer(target, "accept", setup.ticket().toString());
        challenges.answer(target, "accept", setup.ticket().toString());
        assertEquals(1, proxy.requested(target).size());
        assertEquals("duell-1", proxy.requested(target).getFirst().getServerInfo().getName());
        var old = target.getCurrentServer().orElseThrow();
        proxy.connect(target, "duell-1");
        directory.onServerPostConnect(new ServerPostConnectEvent(target, old.getServer()));
        challenges.onConnect(new ServerPostConnectEvent(target, old.getServer()));
        clock.now = clock.now.plusSeconds(61);
        refresh();
        challenges.tick();
        assertEquals(1, proxy.requested(sender).size());
        assertEquals(1, proxy.requested(target).size());
    }

    @Test
    public void expiredInvitationsReturnTheChallengerAndReleaseThePrivateArena() {
        var setup = draft();
        challenges.invite(sender, new ProxyMessage.DuelInvitation(setup.ticket(), new DuelRules("sumo", 1, true, false, 1), "Sumo"));
        clock.now = clock.now.plusSeconds(31);
        refresh();
        challenges.tick();
        assertEquals("lobby-1", proxy.requested(sender).getLast().getServerInfo().getName());
        challenges.answer(target, "accept", setup.ticket().toString());
        assertTrue(proxy.requested(target).isEmpty());
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(stranger.getUniqueId(), "duell-1"));
    }

    @Test
    public void expiredSetupKeepsItsReservationAndReturnsTheLateChallenger() {
        var transfer = proxy.deferNextConnection(sender);
        challenges.request(sender, "Target");
        clock.now = clock.now.plusSeconds(61);
        refresh();
        challenges.tick();
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(stranger.getUniqueId(), "duell-1"));
        var old = sender.getCurrentServer().orElseThrow();
        proxy.connect(sender, "duell-1");
        directory.onServerPostConnect(new ServerPostConnectEvent(sender, old.getServer()));
        challenges.onConnect(new ServerPostConnectEvent(sender, old.getServer()));
        transfer.complete(true);
        assertEquals("lobby-1", proxy.requested(sender).getLast().getServerInfo().getName());
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(stranger.getUniqueId(), "duell-1"));
    }

    @Test
    public void cancellingAnAcceptedTransferReturnsTheLateOpponent() {
        var setup = draft();
        challenges.invite(sender, new ProxyMessage.DuelInvitation(setup.ticket(), new DuelRules("sumo", 1, true, false, 1), "Sumo"));
        var transfer = proxy.deferNextConnection(target);
        challenges.answer(target, "accept", setup.ticket().toString());
        challenges.cancel(sender, setup.ticket());
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(stranger.getUniqueId(), "duell-1"));
        var old = target.getCurrentServer().orElseThrow();
        proxy.connect(target, "duell-1");
        directory.onServerPostConnect(new ServerPostConnectEvent(target, old.getServer()));
        challenges.onConnect(new ServerPostConnectEvent(target, old.getServer()));
        transfer.complete(true);
        assertEquals("lobby-1", proxy.requested(target).getLast().getServerInfo().getName());
    }

    @Test
    public void theInvitationKeepsBothSeatsBeyondTheOrdinarySeatTimeout() {
        var setup = draft();
        challenges.invite(sender, new ProxyMessage.DuelInvitation(setup.ticket(), new DuelRules("sumo", 1, true, false, 1), "Sumo"));
        clock.now = clock.now.plusSeconds(20);
        refresh();
        challenges.answer(target, "accept", setup.ticket().toString());
        assertEquals(1, proxy.requested(target).size());
        assertEquals("duell-1", proxy.requested(target).getFirst().getServerInfo().getName());
    }
}
