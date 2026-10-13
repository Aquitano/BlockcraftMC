package me.blockcraft.system;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.listener.CommandListener;
import net.kyori.adventure.text.Component;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandListenerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void curiousPlayersGetFakeRepliesAndTheThirdPluginRequestChanges() throws Exception {
        var proxy = new FakeProxy();
        var player = proxy.join("Builder");
        var listener = listener();
        for (String command : List.of("pl", "PL", "bukkit:plugins", "version", "pex")) {
            var event = new CommandExecuteEvent(player, command);
            listener.onCommand(event);
            assertFalse(event.getResult().isAllowed());
        }
        assertEquals(List.of(Component.text("Plugins (6): Kaffee"), Component.text("Plugins (6): Kaffee"),
                Component.text("Immer noch Kaffee."), Component.text("Nostalgie"), Component.text("Für uns schon."),
                Component.text("Nicht verfügbar!")), proxy.received(player));
    }

    @Test
    public void staffBypassKeepsTheRealCommands() throws Exception {
        var proxy = new FakeProxy();
        var staff = proxy.join("Staff", SystemPermissions.BLOCKED_COMMANDS_BYPASS);
        var listener = listener();
        var event = new CommandExecuteEvent(staff, "pl");
        listener.onCommand(event);

        assertTrue(event.getResult().isAllowed());
        assertTrue(proxy.received(staff).isEmpty());
    }

    private CommandListener listener() throws Exception {
        var data = folder.newFolder().toPath();
        var config = SystemConfig.load(data.resolve("config.yml"), () -> bytes("""
                blocked-commands: [pl, plugins, 'bukkit:plugins', version, pex]
                """));
        var messages = Messages.load(data, () -> bytes("""
                command-blocked: 'Nicht verfügbar!'
                command-blocked.pl: 'Plugins (6): Kaffee'
                command-blocked.pl-again: 'Immer noch Kaffee.'
                command-blocked.version: ['Nostalgie', 'Für uns schon.']
                """));
        return new CommandListener(config, messages);
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
