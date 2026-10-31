package me.blockcraft.system;

import com.mojang.brigadier.CommandDispatcher;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.staff.GotoCommand;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.report.ReportCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StaffNavigationTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void gotoExplainsWhetherTheStaffMemberNeedsToChangeServers() throws Exception {
        var proxy = new FakeProxy();
        var staff = proxy.join("Staff", SystemPermissions.GOTO);
        var target = proxy.join("Target");
        proxy.connect(staff, "lobby");
        proxy.connect(target, "lobby");
        var messages = Messages.load(folder.getRoot().toPath(), () -> getClass().getResourceAsStream("/messages.yml"));
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        var lookup = new PlayerLookup(proxy.server, visibility, null, messages);
        var directory = new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), counts -> { });
        var dispatcher = new CommandDispatcher<CommandSource>();
        dispatcher.getRoot().addChild(new GotoCommand(new CommandSupport(visibility, messages), lookup, directory, messages)
                .create().getNode());

        dispatcher.execute("goto Target", staff);

        assertTrue(proxy.requested(staff).isEmpty());
        assertEquals(List.of(messages.get("goto.already", Placeholder.unparsed("player", "Target"),
                Placeholder.unparsed("server", "lobby"))), proxy.received(staff));

        var game = proxy.connect(target, "bedwars");
        dispatcher.execute("goto Target", staff);

        assertEquals(List.of(game.getServer()), proxy.requested(staff));
        assertEquals(messages.get("goto.connecting", Placeholder.unparsed("player", "Target"),
                Placeholder.unparsed("server", "bedwars")), proxy.received(staff).getLast());
    }

    @Test
    public void reportNotificationsAreOnlyExposedToStaff() {
        var proxy = new FakeProxy();
        var player = proxy.join("Player");
        var staff = proxy.join("Staff", "blockcraft.report.notify");
        var command = new ReportCommand(null, proxy.server, null, null, null, "blockcraft.report.notify", null, null)
                .create().getNode();

        assertTrue(command.canUse(player));
        assertFalse(command.getChild("notify").canUse(player));
        assertTrue(command.getChild("notify").canUse(staff));
    }
}
