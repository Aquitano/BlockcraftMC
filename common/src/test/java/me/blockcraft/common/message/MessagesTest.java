package me.blockcraft.common.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

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

    private Messages load(String directory, String yaml) throws Exception {
        return Messages.load(folder.newFolder(directory).toPath(),
                () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
