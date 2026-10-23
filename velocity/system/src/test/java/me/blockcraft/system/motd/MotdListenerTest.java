package me.blockcraft.system.motd;

import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

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
}
