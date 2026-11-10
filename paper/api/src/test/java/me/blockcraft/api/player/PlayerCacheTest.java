package me.blockcraft.api.player;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

public class PlayerCacheTest {

    @Test
    public void changesHeardWhileLoadingWinOverTheStoredValues() {
        var player = UUID.randomUUID();
        var cache = new PlayerCache<String, Optional<String>>(Optional.empty());
        cache.set(player, "sounds", Optional.of("off"));
        cache.set(player, "chat-tag", Optional.empty());
        cache.load(player, Map.of("sounds", Optional.of("on"), "chat-tag", Optional.of("old"), "trail", Optional.of("hearts")));
        assertEquals(Optional.of("off"), cache.get(player, "sounds"));
        assertEquals(Optional.empty(), cache.get(player, "chat-tag"));
        assertEquals(Optional.of("hearts"), cache.get(player, "trail"));
        cache.forget(player);
        assertEquals(Optional.empty(), cache.get(player, "sounds"));
    }

    @Test
    public void otherThreadsObservePublishedChanges() throws Exception {
        var player = UUID.randomUUID();
        var cache = new PlayerCache<String, String>("");
        try (var reader = Executors.newSingleThreadExecutor()) {
            for (int index = 0; index < 100; index++) {
                var value = "tag-" + index;
                cache.set(player, "chat-tag", value);
                assertEquals(value, reader.submit(() -> cache.get(player, "chat-tag")).get());
            }
        }
    }
}
