package me.blockcraft.api.settings;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.settings.PlayerSettingRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PlayerSettingRepository} cached per online player, so games can read a choice on the main thread
 * mid-fight. The cache fills right after the join event and {@link PlayerSettingsLoadedEvent} announces it;
 * a read before that answers with the default, not with something wrong. Every change is also reported to the
 * proxy, which honours the same choices for private messages, party and friend invites.
 */
public final class PlayerSettings implements Listener {

    private final Plugin plugin;
    private final PlayerSettingRepository repository;
    private final Map<UUID, Map<PlayerSetting, String>> cache = new ConcurrentHashMap<>();

    public PlayerSettings(Plugin plugin, PlayerSettingRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public Optional<String> get(Player player, PlayerSetting setting) {
        return Optional.ofNullable(cache.getOrDefault(player.getUniqueId(), Map.of()).get(setting));
    }

    /** Whether an on/off setting is on; a player without a stored row is. */
    public boolean enabled(Player player, PlayerSetting setting) {
        return PlayerSetting.enabled(get(player, setting));
    }

    public void set(Player player, PlayerSetting setting, String value) {
        cache.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(PlayerSetting.class)).put(setting, value);
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.set(uuid, setting, value));
        ProxyMessaging.setting(plugin, player, setting, value);
    }

    /** Flips an on/off setting and answers with the state it now has. */
    public boolean toggle(Player player, PlayerSetting setting) {
        boolean now = !enabled(player, setting);
        set(player, setting, PlayerSetting.value(now));
        return now;
    }

    public void clear(Player player, PlayerSetting setting) {
        cache.getOrDefault(player.getUniqueId(), Map.of()).remove(setting);
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.clear(uuid, setting));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.all(uuid), loaded -> {
            cache.merge(uuid, loaded, (fresh, stored) -> {
                stored.putAll(fresh);
                return stored;
            });
            if (player.isOnline()) {
                Bukkit.getPluginManager().callEvent(new PlayerSettingsLoadedEvent(player));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
