package me.blockcraft.api.settings;

import me.blockcraft.common.settings.PlayerSetting;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SettingsCache {

    private final Map<UUID, Map<PlayerSetting, Optional<String>>> players = new ConcurrentHashMap<>();

    Optional<String> get(UUID player, PlayerSetting setting) {
        return players.getOrDefault(player, Map.of()).getOrDefault(setting, Optional.empty());
    }

    void set(UUID player, PlayerSetting setting, Optional<String> value) {
        players.compute(player, (ignored, current) -> {
            var updated = new EnumMap<PlayerSetting, Optional<String>>(PlayerSetting.class);
            if (current != null) {
                updated.putAll(current);
            }
            updated.put(setting, value);
            return Map.copyOf(updated);
        });
    }

    void load(UUID player, Map<PlayerSetting, String> stored) {
        players.compute(player, (ignored, current) -> {
            var loaded = new EnumMap<PlayerSetting, Optional<String>>(PlayerSetting.class);
            stored.forEach((setting, value) -> loaded.put(setting, Optional.of(value)));
            if (current != null) {
                loaded.putAll(current);
            }
            return Map.copyOf(loaded);
        });
    }

    void forget(UUID player) {
        players.remove(player);
    }
}
