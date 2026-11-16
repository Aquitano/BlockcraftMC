package me.blockcraft.system;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.listener.CommandListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandListenerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void blockedCommandsAnswerWithTheirOwnReplyOrTheRefusal() throws Exception {
        var proxy = new FakeProxy();
        var player = proxy.join("Builder");
        var listener = listener();
        for (String command : List.of("PL", "bukkit:plugins", "version", "pex")) {
            var event = new CommandExecuteEvent(player, command);
            listener.onCommand(event);
            assertFalse(event.getResult().isAllowed());
        }
        assertEquals(List.of(Component.text("Kaffee"), Component.text("Kaffee"), Component.text("Nostalgie"),
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

    @Test
    public void everyHelpCommandSuggestsItselfAndExplainsItselfOnHover() throws Exception {
        var loaded = Messages.load(folder.newFolder().toPath(), () -> getClass().getResourceAsStream("/messages.yml"));
        var commands = loaded.with(loaded.tagsOf("palette")).getLines("help").stream()
                .filter(line -> PlainTextComponentSerializer.plainText().serialize(line).strip().startsWith("/"))
                .toList();
        assertTrue(commands.size() > 20);
        for (var line : commands) {
            String shown = PlainTextComponentSerializer.plainText().serialize(line).strip();
            var button = flatten(line).filter(part -> part.clickEvent() != null).findFirst().orElseThrow(() -> new AssertionError(shown));
            assertEquals(shown, ClickEvent.Action.SUGGEST_COMMAND, button.clickEvent().action());
            assertTrue(shown, shown.startsWith(((ClickEvent.Payload.Text) button.clickEvent().payload()).value().strip()));
            assertTrue(shown, button.hoverEvent() != null);
        }
    }

    private static Stream<Component> flatten(Component component) {
        return Stream.concat(Stream.of(component), component.children().stream().flatMap(CommandListenerTest::flatten));
    }

    private CommandListener listener() throws Exception {
        var data = folder.newFolder().toPath();
        var config = SystemConfig.load(data.resolve("config.yml"), () -> bytes("""
                blocked-commands: [pl, plugins, 'bukkit:plugins', version, pex]
                """));
        var messages = Messages.load(data, () -> bytes("""
                command-blocked: 'Nicht verfügbar!'
                blocked-replies:
                  pl: &plugins ['Kaffee']
                  plugins: *plugins
                  version: 'Nostalgie'
                """));
        return new CommandListener(config, messages);
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
