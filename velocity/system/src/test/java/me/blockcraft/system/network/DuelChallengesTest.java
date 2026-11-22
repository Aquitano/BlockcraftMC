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
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
    private Messages messages;
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
        messages = Messages.load(data, () -> getClass().getResourceAsStream("/messages.yml"));
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

    private static final DuelRules RULES = new DuelRules("sumo", true, 3);

    private ProxyMessage.DuelSetup draft() {
        var lobby = sender.getCurrentServer().orElseThrow();
        challenges.request(sender, "Target");
        assertTrue(proxy.requested(sender).isEmpty());
        return (ProxyMessage.DuelSetup) ProxyMessageCodec.decode(proxy.sentTo(lobby).getLast(), ProxyMessage.ToBackend.class).orElseThrow();
    }

    private ProxyMessage.DuelSetup invite() {
        var setup = draft();
        challenges.invite(sender, new ProxyMessage.DuelInvitation(setup.ticket(), RULES));
        return setup;
    }

    private ProxyMessage.ToBackend arrive(Player player) {
        var old = player.getCurrentServer().orElseThrow();
        var connection = proxy.connect(player, "duell-1");
        directory.onServerPostConnect(new ServerPostConnectEvent(player, old.getServer()));
        challenges.onConnect(new ServerPostConnectEvent(player, old.getServer()));
        return ProxyMessageCodec.decode(proxy.sentTo(connection).getLast(), ProxyMessage.ToBackend.class).orElseThrow();
    }

    @Test
    public void onlyTheInvitedPlayerCanAcceptAndBothArriveWithTheRules() {
        var setup = invite();
        challenges.answer(stranger, "accept", setup.ticket().toString());
        challenges.answer(target, "accept", UUID.randomUUID().toString());
        assertTrue(proxy.requested(target).isEmpty());
        challenges.answer(target, "accept", setup.ticket().toString());
        challenges.answer(target, "accept", setup.ticket().toString());
        assertEquals(List.of("duell-1"), proxy.requested(sender).stream().map(server -> server.getServerInfo().getName()).toList());
        assertEquals(List.of("duell-1"), proxy.requested(target).stream().map(server -> server.getServerInfo().getName()).toList());
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(stranger.getUniqueId(), "duell-1"));
        assertEquals(new ProxyMessage.DuelMatch(setup.ticket(), target.getUniqueId(), RULES), arrive(sender));
        assertEquals(new ProxyMessage.DuelMatch(setup.ticket(), sender.getUniqueId(), RULES), arrive(target));
        clock.now = clock.now.plusSeconds(61);
        refresh();
        challenges.tick();
        assertEquals(1, proxy.requested(sender).size());
        assertEquals(1, proxy.requested(target).size());
    }

    @Test
    public void strikingTheChallengerBackAcceptsTheChallenge() {
        var setup = invite();
        challenges.request(target, sender.getUniqueId());
        assertEquals("duell-1", proxy.requested(target).getLast().getServerInfo().getName());
        assertEquals(new ProxyMessage.DuelMatch(setup.ticket(), sender.getUniqueId(), RULES), arrive(target));
    }

    @Test
    public void expiredInvitationsCannotBeAccepted() {
        var setup = invite();
        clock.now = clock.now.plusSeconds(31);
        challenges.request(target, sender.getUniqueId());
        refresh();
        challenges.tick();
        challenges.answer(target, "accept", setup.ticket().toString());
        assertTrue(proxy.requested(sender).isEmpty());
        assertTrue(proxy.requested(target).isEmpty());
    }

    @Test
    public void aTargetIsOnlyTiedUpOnceInvited() {
        var setup = draft();
        var lobby = stranger.getCurrentServer().orElseThrow();
        challenges.request(stranger, "Target");
        var other = (ProxyMessage.DuelSetup) ProxyMessageCodec.decode(proxy.sentTo(lobby).getLast(), ProxyMessage.ToBackend.class).orElseThrow();
        challenges.invite(stranger, new ProxyMessage.DuelInvitation(other.ticket(), RULES));
        challenges.invite(sender, new ProxyMessage.DuelInvitation(setup.ticket(), RULES));
        assertEquals(messages.get("duel.target-busy", Placeholder.unparsed("player", "Target")), proxy.received(sender).getLast());
    }

    @Test
    public void theChallengerCannotAcceptTheirOwnChallenge() {
        var setup = invite();
        challenges.answer(sender, "accept", setup.ticket().toString());
        challenges.request(sender, target.getUniqueId());
        assertTrue(proxy.requested(sender).isEmpty());
        assertTrue(proxy.requested(target).isEmpty());
    }

    @Test
    public void cancellingAnAcceptedTransferReturnsTheLateOpponent() {
        var setup = invite();
        var transfer = proxy.deferNextConnection(target);
        challenges.answer(target, "accept", setup.ticket().toString());
        arrive(sender);
        challenges.cancel(sender, setup.ticket());
        assertEquals("lobby-1", proxy.requested(sender).getLast().getServerInfo().getName());
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(stranger.getUniqueId(), "duell-1"));
        var old = target.getCurrentServer().orElseThrow();
        proxy.connect(target, "duell-1");
        directory.onServerPostConnect(new ServerPostConnectEvent(target, old.getServer()));
        challenges.onConnect(new ServerPostConnectEvent(target, old.getServer()));
        transfer.complete(true);
        assertEquals("lobby-1", proxy.requested(target).getLast().getServerInfo().getName());
    }

    @Test
    public void theChallengerLearnsWhyNoDuelStartsAndHowLongTheCooldownLasts() {
        challenges.request(sender, "Sender");
        assertEquals(messages.get("duel.self"), proxy.received(sender).getLast());
        var setup = draft();
        challenges.cancel(sender, setup.ticket());
        proxy.connect(sender, "duell-1");
        challenges.request(sender, "Target");
        assertEquals(messages.get("duel.busy.not-in-lobby"), proxy.received(sender).getLast());
        proxy.connect(sender, "lobby-1");
        clock.now = clock.now.plusSeconds(4);
        challenges.request(sender, "Target");
        assertEquals(messages.get("duel.cooldown", Formatter.number("seconds", 6)), proxy.received(sender).getLast());
    }

    @Test
    public void aDeclineTellsTheChallengerWhoDeclined() {
        var setup = invite();
        challenges.answer(target, "decline", setup.ticket().toString());
        assertTrue(proxy.received(sender).contains(messages.get("duel.declined", Placeholder.unparsed("player", "Target"))));
        assertEquals(messages.get("duel.you-declined"), proxy.received(target).getLast());
    }
}
