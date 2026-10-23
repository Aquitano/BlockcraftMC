package me.blockcraft.api.settings;

import me.blockcraft.common.settings.PlayerSetting;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

public class SettingsCacheTest {

    @Test
    public void initialReadPreservesChoicesChangedOrClearedWhileLoading() {
        var player = UUID.randomUUID();
        var cache = new SettingsCache();
        cache.set(player, PlayerSetting.SOUNDS, Optional.of(PlayerSetting.OFF));
        cache.set(player, PlayerSetting.CHAT_TAG, Optional.empty());
        cache.load(player, Map.of(PlayerSetting.SOUNDS, PlayerSetting.ON, PlayerSetting.CHAT_TAG, "old",
                PlayerSetting.TRAIL, "hearts"));
        assertEquals(Optional.of(PlayerSetting.OFF), cache.get(player, PlayerSetting.SOUNDS));
        assertEquals(Optional.empty(), cache.get(player, PlayerSetting.CHAT_TAG));
        assertEquals(Optional.of("hearts"), cache.get(player, PlayerSetting.TRAIL));
        cache.forget(player);
        assertEquals(Optional.empty(), cache.get(player, PlayerSetting.SOUNDS));
    }

    @Test
    public void chatReadersObservePublishedChanges() throws Exception {
        var player = UUID.randomUUID();
        var cache = new SettingsCache();
        try (var reader = Executors.newSingleThreadExecutor()) {
            for (int index = 0; index < 100; index++) {
                var value = Optional.of("tag-" + index);
                cache.set(player, PlayerSetting.CHAT_TAG, value);
                assertEquals(value, reader.submit(() -> cache.get(player, PlayerSetting.CHAT_TAG)).get());
            }
        }
    }
}
