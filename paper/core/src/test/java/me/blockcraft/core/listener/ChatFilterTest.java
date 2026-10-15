package me.blockcraft.core.listener;

import me.blockcraft.common.config.YamlFile;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatFilterTest {

    private static final ChatFilter FILTER = new ChatFilter(ChatFilterConfig.fromYaml(bundledConfig()));

    @Test
    public void everydayChatIsNoAdvertisement() {
        assertFalse(FILTER.isAdvertisement("ok.denke ich"));
        assertFalse(FILTER.isAdvertisement("hmm...toll"));
        assertFalse(FILTER.isAdvertisement("und eine runde noch"));
        assertFalse(FILTER.isAdvertisement("kommt auf play.BlockcraftMC.net"));
        assertFalse(FILTER.isAdvertisement("wir starten um 12:20"));
    }

    @Test
    public void namesAreNoInsults() {
        assertFalse(FILTER.hasBlockedWord("Sebastian kommt gleich"));
    }

    @Test
    public void otherServersAreAdvertisements() {
        assertTrue(FILTER.isAdvertisement("kommt auf play.gommehd.net"));
        assertTrue(FILTER.isAdvertisement("blockcraft.de ist besser"));
        assertTrue(FILTER.isAdvertisement("connect 85,214,3,9"));
        assertTrue(FILTER.isAdvertisement("join myserver:25565"));
    }

    private static Map<String, Object> bundledConfig() {
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            return YamlFile.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
