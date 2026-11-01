package me.blockcraft.system;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.nick.Nick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerIdentityTest {

    @Test
    public void suggestionsAndLookupRespectNickAndVanish() {
        var proxy = new FakeProxy();
        var viewer = proxy.join("Viewer");
        var target = proxy.join("Account");
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        var lookup = new PlayerLookup(proxy.server, visibility, null, null);

        visibility.setNick(target, Optional.of(new Nick("Nickname", "Notch", "default")));
        assertEquals(List.of("Viewer", "Nickname"), visibility.namesFor(viewer).toList());
        assertEquals(target.getUniqueId(), lookup.findVisible(viewer, "Nickname", Component.empty()).orElseThrow().uuid());
        assertTrue(lookup.findVisible(viewer, "Account", Component.empty()).isEmpty());

        visibility.setVanished(target, true);
        assertEquals(List.of("Viewer"), visibility.namesFor(viewer).toList());
        assertTrue(lookup.findVisible(viewer, "Nickname", Component.empty()).isEmpty());
        assertTrue(lookup.findVisible(viewer, "Account", Component.empty()).isEmpty());

        var staff = proxy.join("Staff", SystemPermissions.SEE_NICKS, SystemPermissions.SEE_VANISHED);
        assertTrue(visibility.namesFor(staff).toList().contains("Account"));
        assertEquals(target.getUniqueId(), lookup.findVisible(staff, "Account", Component.empty()).orElseThrow().uuid());
    }

    @Test
    public void aRefusedDuplicateLoginLeavesTheLiveSessionVanished() {
        var proxy = new FakeProxy();
        var staff = proxy.join("Staff");
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        visibility.setVanished(staff, true);

        visibility.onDisconnect(new DisconnectEvent(staff, DisconnectEvent.LoginStatus.CONFLICTING_LOGIN));
        assertTrue(visibility.isVanished(staff));

        visibility.onDisconnect(new DisconnectEvent(staff, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
        assertFalse(visibility.isVanished(staff));
    }

    @Test
    public void lookupRefusesANameThatTwoOnlinePlayersGoBy() throws Exception {
        var proxy = new FakeProxy();
        var viewer = proxy.join("Viewer");
        proxy.join("Bob");
        var nicked = proxy.join("Account");
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        var messages = Messages.load(Files.createTempDirectory("lookup-messages"), () -> PlayerIdentityTest.class.getResourceAsStream("/messages.yml"));
        var lookup = new PlayerLookup(proxy.server, visibility, null, messages);
        visibility.setNick(nicked, Optional.of(new Nick("Bob", "Notch", "default")));

        assertTrue(lookup.online(viewer, "Bob", Component.empty()).isEmpty());
        assertTrue(lookup.find(viewer, "Bob", Component.empty()).isEmpty());
        assertEquals(messages.get("player-ambiguous", Placeholder.unparsed("player", "Bob")), proxy.received(viewer).getFirst());
    }

}
