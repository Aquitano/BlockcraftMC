package me.blockcraft.common.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MessagesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void sharedAnnouncementsUseTheGamePrefixAndKeepTheNetworkPalette() throws Exception {
        var shared = load("shared", """
                prefix: '<blue>Blockcraft <gray>» '
                joined: '<prefix><white><player> joined'
                """);
        var game = load("game", "prefix: '<quicksg>QuickSG <gray>» '");
        var themed = shared.with(Placeholder.parsed("quicksg", "<gold>"));
        var player = Placeholder.component("player", Component.text("Builder"));

        assertEquals(MiniMessage.miniMessage().deserialize("<gold>QuickSG <gray>» <white>Builder joined"),
                themed.prefixedLike(game).get("joined", player));
        assertEquals(MiniMessage.miniMessage().deserialize("<blue>Blockcraft <gray>» <white>Builder joined"),
                themed.get("joined", player));
    }

    @Test
    public void picksOneLocalizedLineWithTheSamePrefixAndPlaceholders() throws Exception {
        var messages = load("choices", """
                prefix: '<blue>Blockcraft <gray>» '
                choices:
                  - '<prefix><white>Hello <player>'
                  - '<prefix><white>Welcome <player>'
                """);
        var player = Placeholder.unparsed("player", "<Builder>");
        var mini = MiniMessage.miniMessage();
        var expected = List.of(
                mini.deserialize("<blue>Blockcraft <gray>» <white>Hello <player>", player),
                mini.deserialize("<blue>Blockcraft <gray>» <white>Welcome <player>", player));

        assertTrue(expected.contains(messages.pick("choices", player)));
        assertEquals(2, messages.getLines("choices", player).size());
    }

    @Test
    public void aServerEditedScalarOverridesTheBundledChoiceList() throws Exception {
        var directory = folder.newFolder("override").toPath();
        Files.writeString(directory.resolve("messages.yml"), "greeting: '<green>My own <player>'");
        var messages = Messages.load(directory, () -> new ByteArrayInputStream("""
                greeting:
                  - '<blue>Hello <player>'
                  - '<blue>Welcome <player>'
                """.getBytes(StandardCharsets.UTF_8)));
        var player = Placeholder.unparsed("player", "Builder");

        assertEquals(messages.get("greeting", player), messages.pick("greeting", player));
    }

    private Messages load(String directory, String yaml) throws Exception {
        return Messages.load(folder.newFolder(directory).toPath(),
                () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
