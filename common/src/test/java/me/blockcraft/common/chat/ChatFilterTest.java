package me.blockcraft.common.chat;

import me.blockcraft.common.config.YamlFile;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatFilterTest {

    private static final ChatFilter FILTER = ChatFilter.fromYaml(bundledLists(), List.of("BlockcraftMC.net"));

    @Test
    public void everydayChatIsNoAdvertisement() {
        assertFalse(FILTER.isAdvertisement("ok.denke ich"));
        assertFalse(FILTER.isAdvertisement("hmm...toll"));
        assertFalse(FILTER.isAdvertisement("und eine runde noch"));
        assertFalse(FILTER.isAdvertisement("kommt auf play.BlockcraftMC.net"));
        assertFalse(FILTER.isAdvertisement("wir starten um 12:20"));
        assertFalse(FILTER.isAdvertisement("kaufe 64 32 16 8"));
        assertFalse(FILTER.isAdvertisement("3 2 1 0 go"));
        assertFalse(FILTER.isAdvertisement("naja...es geht"));
        assertFalse(FILTER.isAdvertisement("ja.es war"));
        assertFalse(FILTER.isAdvertisement("knapp...gg"));
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
        assertTrue(FILTER.isAdvertisement("connect 85 . 214 . 3 . 9"));
        assertTrue(FILTER.isAdvertisement("join discord.gg/abcdef"));
        assertTrue(FILTER.isAdvertisement("play.example.gg"));
        assertTrue(FILTER.isAdvertisement("mc.example.at"));
        assertTrue(FILTER.isAdvertisement("mc.example.io"));
        assertTrue(FILTER.isAdvertisement("mc.example.xyz"));
        assertTrue(FILTER.isAdvertisement("join myserver:25565"));
    }

    @Test
    public void theNetworksOwnAddressesAreNoAdvertisement() {
        var filter = ChatFilter.fromYaml(bundledLists(), List.of("mc.example.org", "https://shop.example.org"));

        assertFalse(filter.isAdvertisement("kommt auf mc.example.org"));
        assertFalse(filter.isAdvertisement("ränge gibt es auf shop.example.org"));
        assertTrue(filter.isAdvertisement("kommt auf play.example.net"));
        assertTrue(filter.isAdvertisement("kommt auf notmc.example.org"));
        assertTrue(filter.isAdvertisement("kommt auf mc.example.org.ru"));
    }

    private static Map<String, Object> bundledLists() {
        try (var in = Files.newInputStream(Path.of("src/main/resources/chat-filter.yml"))) {
            return YamlFile.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
