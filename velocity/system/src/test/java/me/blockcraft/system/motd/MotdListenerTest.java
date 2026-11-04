package me.blockcraft.system.motd;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MotdListenerTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void brandedDefaultUsesTheConfiguredAddressAndAllowsLegacyOverrides() throws Exception {
        var directory = folder.getRoot().toPath();
        Files.writeString(directory.resolve("config.yml"), "network:\n  ip: Example.test\n");
        var config = SystemConfig.load(directory.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        var loaded = Messages.load(directory, () -> getClass().getResourceAsStream("/messages.yml"));
        var messages = loaded.with(config.networkTags(), loaded.tagsOf("palette"));
        var legacy = LegacyComponentSerializer.legacyAmpersand();

        assertTrue(legacy.serialize(MotdListener.firstLine(config, messages)).contains("&9&lE X A M P L E . T E S T"));

        config.setDefaultMotd("&cEigene erste Zeile");
        assertEquals(legacy.deserialize("&cEigene erste Zeile"), MotdListener.firstLine(config, messages));
    }

    @Test
    public void theMostSpecialActiveOccasionNamesTheSecondLine() throws Exception {
        var directory = folder.getRoot().toPath();
        var config = SystemConfig.load(directory.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        var loaded = Messages.load(directory, () -> getClass().getResourceAsStream("/messages.yml"));
        var messages = loaded.with(config.networkTags(), loaded.tagsOf("palette"));
        var calendar = config.calendar();
        var plain = PlainTextComponentSerializer.plainText();

        calendar.force(Optional.of(Set.of(Occasion.WINTER, Occasion.ADVENT, Occasion.CHRISTMAS)));
        assertEquals(Optional.of(plain.serialize(messages.get("motd.season.christmas"))),
                MotdListener.seasonalLine(calendar, messages).map(plain::serialize));

        calendar.force(Optional.of(Set.of()));
        assertEquals(Optional.empty(), MotdListener.seasonalLine(calendar, messages));
    }
}
