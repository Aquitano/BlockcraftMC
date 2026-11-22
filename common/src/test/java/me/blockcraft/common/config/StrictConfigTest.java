package me.blockcraft.common.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StrictConfigTest {

    @Test
    public void readsNestedKeysAndNamesTheFullPathOfAMissingOrMistypedOne() {
        var config = StrictConfig.of("config.yml", Map.of("discovery", Map.of("poll-seconds", 5, "url", "http://waker")));
        var discovery = config.section("discovery");

        assertEquals(5, discovery.integer("poll-seconds"));
        assertEquals("http://waker", config.text("discovery.url"));
        assertEquals("config.yml: discovery.stale-seconds is missing",
                assertThrows(IllegalStateException.class, () -> discovery.integer("stale-seconds")).getMessage());
        assertEquals("config.yml: discovery.url must be a whole number, not http://waker",
                assertThrows(IllegalStateException.class, () -> discovery.integer("url")).getMessage());
    }
}
