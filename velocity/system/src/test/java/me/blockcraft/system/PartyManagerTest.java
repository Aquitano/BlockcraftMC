package me.blockcraft.system;

import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import org.junit.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PartyManagerTest {

    @Test
    public void invitationsFollowPromotionAndDisconnectHandsOverInJoinOrder() throws Exception {
        var proxy = new FakeProxy();
        var choices = new PlayerChoices(null, null);
        var messages = Messages.load(Files.createTempDirectory("party-messages"), () -> PartyManagerTest.class.getResourceAsStream("/messages.yml"));
        var parties = new PartyManager(proxy.server, new PlayerVisibility(proxy.server, null, null, null), choices,
                new IgnoreList(null, null), new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), counts -> { }), messages);
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
        var proxy = new FakeProxy();
        var choices = new PlayerChoices(null, null);
        var messages = Messages.load(Files.createTempDirectory("party-messages"), () -> PartyManagerTest.class.getResourceAsStream("/messages.yml"));
        var parties = new PartyManager(proxy.server, new PlayerVisibility(proxy.server, null, null, null), choices,
                new IgnoreList(null, null), new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), counts -> { }), messages);
        var leader = proxy.join("Leader");
        var stale = proxy.join("Stale");
        choices.set(stale, PlayerSetting.PARTY_INVITES, PlayerSetting.value(true));

        parties.invite(leader, stale);
        parties.leave(leader);
        parties.create(leader);
        parties.accept(stale);
        assertFalse(parties.isInParty(stale.getUniqueId()));
    }
}
