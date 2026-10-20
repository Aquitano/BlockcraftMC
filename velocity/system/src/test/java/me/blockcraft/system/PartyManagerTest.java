package me.blockcraft.system;

import com.mojang.brigadier.CommandDispatcher;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.player.PartyCommand;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PartyManagerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private FakeProxy proxy;
    private PlayerChoices choices;
    private PlayerVisibility visibility;
    private Messages messages;
    private PartyManager parties;

    @Before
    public void setUp() throws Exception {
        proxy = new FakeProxy();
        choices = new PlayerChoices(null, null);
        visibility = new PlayerVisibility(proxy.server, null, null, null);
        messages = Messages.load(folder.newFolder().toPath(), () -> PartyManagerTest.class.getResourceAsStream("/messages.yml"));
        parties = new PartyManager(proxy.server, visibility, choices, new IgnoreList(null, null),
                new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), counts -> { }), messages);
    }

    @Test
    public void invitationsFollowPromotionAndDisconnectHandsOverInJoinOrder() throws Exception {
        var leader = proxy.join("Leader");
        var member = proxy.join("Member");
        var invited = proxy.join("Invited");
        for (var player : List.of(leader, member, invited)) {
            choices.set(player, PlayerSetting.PARTY_INVITES, PlayerSetting.value(true));
        }

        parties.invite(leader, member);
        parties.accept(member);
        parties.invite(leader, invited);
        parties.promote(leader, member);
        parties.accept(invited);
        assertEquals(member.getUniqueId(), parties.partyOf(invited.getUniqueId()).orElseThrow().leader());

        proxy.leave(member);
        parties.onDisconnect(member);
        assertTrue(parties.isLeader(leader.getUniqueId()));
        assertEquals(leader.getUniqueId(), parties.partyOf(invited.getUniqueId()).orElseThrow().leader());
    }

    @Test
    public void invitationDiesWithTheParty() throws Exception {
        var leader = proxy.join("Leader");
        var stale = proxy.join("Stale");
        choices.set(stale, PlayerSetting.PARTY_INVITES, PlayerSetting.value(true));

        parties.invite(leader, stale);
        parties.leave(leader);
        parties.create(leader);
        parties.accept(stale);
        assertFalse(parties.isInParty(stale.getUniqueId()));
    }

    @Test
    public void aRefusedInvitationDoesNotCreateAPartyOrSendACreationMessage() {
        var inviter = proxy.join("Inviter");
        var target = proxy.join("Target");
        choices.set(target, PlayerSetting.PARTY_INVITES, PlayerSetting.value(false));

        parties.invite(inviter, target);

        assertFalse(parties.isInParty(inviter.getUniqueId()));
        assertEquals(List.of(messages.get("party.invites-disabled", Placeholder.unparsed("player", "Target"))), proxy.received(inviter));
        assertTrue(proxy.received(target).isEmpty());
        parties.accept(target);
        assertFalse(parties.isInParty(target.getUniqueId()));
        assertEquals(messages.get("party.not-invited"), proxy.received(target).getLast());
    }

    @Test
    public void invitingSomeoneAlreadyInAPartyLeavesTheInviterOutside() {
        var inviter = proxy.join("Inviter");
        var target = proxy.join("Target");
        parties.create(target);

        parties.invite(inviter, target);

        assertFalse(parties.isInParty(inviter.getUniqueId()));
        assertEquals(List.of(messages.get("party.target-in-party")), proxy.received(inviter));
        assertTrue(parties.isLeader(target.getUniqueId()));
    }

    @Test
    public void membersCannotInviteKickOrPromote() {
        var leader = proxy.join("Leader");
        var member = proxy.join("Member");
        var target = proxy.join("Target");
        choices.set(member, PlayerSetting.PARTY_INVITES, PlayerSetting.value(true));
        parties.invite(leader, member);
        parties.accept(member);

        parties.invite(member, target);
        assertEquals(messages.get("party.only-leader-invites"), proxy.received(member).getLast());
        parties.kick(member, leader);
        assertEquals(messages.get("party.only-leader-kicks"), proxy.received(member).getLast());
        parties.promote(member, leader);
        assertEquals(messages.get("party.only-leader-promotes"), proxy.received(member).getLast());

        assertEquals(leader.getUniqueId(), parties.partyOf(member.getUniqueId()).orElseThrow().leader());
        assertTrue(parties.partyOf(leader.getUniqueId()).orElseThrow().members().contains(member.getUniqueId()));
        assertFalse(parties.isInParty(target.getUniqueId()));
        assertTrue(proxy.received(target).isEmpty());
    }

    @Test
    public void anExistingMemberCannotRequestAnotherInvitationOrConsumeTheRequestCooldown() {
        var leader = proxy.join("Leader");
        var member = proxy.join("Member");
        var otherLeader = proxy.join("OtherLeader");
        choices.set(member, PlayerSetting.PARTY_INVITES, PlayerSetting.value(true));
        parties.invite(leader, member);
        parties.accept(member);
        parties.create(otherLeader);
        int receivedByOtherLeader = proxy.received(otherLeader).size();

        parties.requestInvite(member, otherLeader);

        assertEquals(messages.get("party.already-in-party"), proxy.received(member).getLast());
        assertEquals(receivedByOtherLeader, proxy.received(otherLeader).size());
        assertEquals(leader.getUniqueId(), parties.partyOf(member.getUniqueId()).orElseThrow().leader());

        parties.leave(member);
        parties.requestInvite(member, otherLeader);

        assertEquals(messages.get("party.join-requested", Placeholder.unparsed("player", "OtherLeader")), proxy.received(member).getLast());
        assertEquals(receivedByOtherLeader + 2, proxy.received(otherLeader).size());
    }

    @Test
    public void partyChatParsesLegacyCodesOnlyForSendersWhoMayColour() {
        var leader = proxy.join("Leader");
        var member = proxy.join("Member", SystemPermissions.CHAT_COLOR);
        choices.set(member, PlayerSetting.PARTY_INVITES, PlayerSetting.value(true));
        parties.invite(leader, member);
        parties.accept(member);

        parties.chat(leader, "&kplain");
        assertEquals(chatLine("Leader", Component.text("&kplain")), proxy.received(member).getLast());

        parties.chat(member, "&acoloured");
        assertEquals(chatLine("Member", LegacyComponentSerializer.legacyAmpersand().deserialize("&acoloured")), proxy.received(leader).getLast());
    }

    @Test
    public void commandsKeepPermissionAndVisibilityChecksBeforePartyChanges() throws Exception {
        var commands = new CommandDispatcher<CommandSource>();
        commands.getRoot().addChild(new PartyCommand(new CommandSupport(visibility, messages), new PlayerLookup(proxy.server, visibility, null, messages), parties, messages).create().getNode());
        var ordinary = proxy.join("Ordinary");
        var inviter = proxy.join("Inviter", SystemPermissions.PARTY_INVITE);
        var target = proxy.join("Target");

        commands.execute("party invite Target", ordinary);
        assertEquals(messages.get("party.invite-no-permission"), proxy.received(ordinary).getLast());
        commands.execute("party kick Target", ordinary);
        assertEquals(messages.get("party.kick-no-permission"), proxy.received(ordinary).getLast());
        visibility.setVanished(target, true);
        commands.execute("party invite Target", inviter);
        assertEquals(messages.get("party.target-offline"), proxy.received(inviter).getLast());

        assertFalse(parties.isInParty(ordinary.getUniqueId()));
        assertFalse(parties.isInParty(inviter.getUniqueId()));
        assertTrue(proxy.received(target).isEmpty());
    }

    private Component chatLine(String sender, Component message) {
        return messages.get("party.chat", Placeholder.unparsed("player", sender), Placeholder.component("message", message));
    }
}
