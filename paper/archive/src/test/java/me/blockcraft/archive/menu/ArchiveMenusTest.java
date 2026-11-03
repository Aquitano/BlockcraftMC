package me.blockcraft.archive.menu;

import me.blockcraft.archive.ArchiveConfig;
import me.blockcraft.archive.exhibit.ExhibitCollection;
import me.blockcraft.archive.exhibit.ExhibitPool;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.helpers.NOPLogger;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArchiveMenusTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void suggestionsKeepConfiguredOrderAndSkipDuplicatesAndUnavailableMaps() throws Exception {
        var pool = pool();
        write("lobby", "Blockcraft");
        write("summer", "ClayMC");
        var config = YamlConfiguration.loadConfiguration(new StringReader("""
                guide:
                  suggested-visits: [summer, missing, lobby, summer]
                """));
        var menus = new ArchiveMenus(null, pool, null, null, null, ArchiveConfig.from(config));

        assertEquals(List.of("summer", "lobby"), menus.suggestedExhibits().stream().map(exhibit -> exhibit.key()).toList());
        assertTrue(menus.suggested(pool.exhibit("summer").orElseThrow()));
    }

    @Test
    public void categoryTitlesKeepTheirServerAndEveryBundledVisitHasCheckpoints() throws Exception {
        var config = ArchiveConfig.from(YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)));
        var messages = Messages.load(temporary.newFolder("messages").toPath(), () -> getClass().getResourceAsStream("/messages.yml"),
                Placeholder.parsed("arrow", ""), Placeholder.parsed("archive", "<gold>"),
                Placeholder.parsed("dark", "<dark_gray>"), Placeholder.parsed("muted", "<gray>"));
        var pool = new ExhibitPool(Path.of("../../worlds/archive-exhibits"), NOPLogger.NOP_LOGGER);
        var menus = new ArchiveMenus(messages, pool, null, null, null, config);
        var blockcraft = ExhibitCollection.of(pool.exhibits(), config.collectionOrder()).getFirst();
        var section = menus.sections(blockcraft).stream().filter(value -> value.category().equals("Lobbys")).findFirst().orElseThrow();

        assertEquals("Blockcraft 2015–16 · Lobbys", PlainTextComponentSerializer.plainText().serialize(menus.sectionName(section)));
        assertEquals(config.suggestedVisits(), menus.suggestedExhibits().stream().map(exhibit -> exhibit.key()).toList());
        assertTrue(menus.suggestedExhibits().stream().allMatch(exhibit -> !exhibit.checkpoints().isEmpty()));
    }

    private ExhibitPool pool() {
        return new ExhibitPool(temporary.getRoot().toPath(), NOPLogger.NOP_LOGGER);
    }

    private void write(String key, String origin) throws Exception {
        var folder = temporary.getRoot().toPath().resolve(key);
        Files.createDirectories(folder.resolve("world"));
        Files.writeString(folder.resolve("exhibit.yml"), "origin: " + origin + "\ncategory: Lobbys\n");
    }
}
