package me.blockcraft.api.settings;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.SerialTasks;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@link PlayerSettingRepository} cached per online player, so games can read a choice on the main thread
 * mid-fight. The cache fills right after the join event and {@link PlayerSettingsLoadedEvent} announces it;
 * a read before that answers with the default, not with something wrong. Every change is also reported to the
 * proxy, which honours the same choices for private messages, party and friend invites.
 */
public final class PlayerSettings implements Listener {

    private final Plugin plugin;
    private final PlayerSettingRepository repository;
    private final SettingsCache cache = new SettingsCache();

    private final SerialTasks<UUID> writes;
    private final Map<UUID, Map<PlayerSetting, Object>> revisions = new HashMap<>();

    public PlayerSettings(Plugin plugin, PlayerSettingRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.writes = new SerialTasks<>(plugin);
    }

    public Optional<String> get(Player player, PlayerSetting setting) {
        return cache.get(player.getUniqueId(), setting);
    }

    /** Whether an on/off setting is on; a player without a stored row has the setting's default. */
    public boolean enabled(Player player, PlayerSetting setting) {
        return allowed(player, setting) && setting.enabled(get(player, setting));
    }

    /** A toggle guarded by a permission is only the player's to choose, and to be judged on, while they hold it. */
    public static boolean allowed(Player player, PlayerSetting setting) {
        return setting.permission().map(player::hasPermission).orElse(true);
    }

    public CompletableFuture<Boolean> set(Player player, PlayerSetting setting, String value) {
        if (get(player, setting).filter(value::equals).isPresent()) {
            return writes.submit(player.getUniqueId(), () -> true);
        }
        return change(player, setting, Optional.of(value));
    }

    private CompletableFuture<Boolean> change(Player player, PlayerSetting setting, Optional<String> value) {
        var uuid = player.getUniqueId();
        var revision = new Object();
        revisions.computeIfAbsent(uuid, ignored -> new EnumMap<>(PlayerSetting.class)).put(setting, revision);
        var previousValue = get(player, setting);
        var completion = new CompletableFuture<Boolean>();
        apply(player, setting, value);
        writes.submit(uuid, () -> save(uuid, setting, value, previousValue), saved -> {
            if (player.isOnline() && revisions.getOrDefault(uuid, Map.of()).get(setting) == revision && !saved.success()) {
                apply(player, setting, saved.stored());
                player.sendMessage(BlockcraftApi.get().messages().get("settings.failed"));
            }
            completion.complete(saved.success());
        }, () -> completion.complete(false));
        return completion;
    }

    private record Saved(boolean success, Optional<String> stored) {
    }

    private Saved save(UUID uuid, PlayerSetting setting, Optional<String> value, Optional<String> previousValue) {
        try {
            if (value.isPresent()) {
                repository.set(uuid, setting, value.get());
            } else {
                repository.clear(uuid, setting);
            }
            return new Saved(true, value);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save setting " + setting.key() + " for " + uuid, error);
            try {
                return new Saved(false, repository.get(uuid, setting));
            } catch (RuntimeException readError) {
                plugin.getLogger().log(Level.WARNING, "Could not reload setting " + setting.key() + " for " + uuid, readError);
                return new Saved(false, previousValue);
            }
        }
    }

    private void apply(Player player, PlayerSetting setting, Optional<String> value) {
        cache.set(player.getUniqueId(), setting, value);
        ProxyMessaging.setting(plugin, player, setting, value.orElse(setting.isToggle()
                ? PlayerSetting.value(setting.enabled(Optional.empty())) : ""));
        new PlayerSettingChangedEvent(player, setting).callEvent();
    }

    /** Flips an on/off setting and answers with the state it now has. */
    public boolean toggle(Player player, PlayerSetting setting) {
        boolean now = !enabled(player, setting);
        set(player, setting, PlayerSetting.value(now));
        return now;
    }

    public CompletableFuture<Boolean> clear(Player player, PlayerSetting setting) {
        return change(player, setting, Optional.empty());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var session = new EnumMap<PlayerSetting, Object>(PlayerSetting.class);
        revisions.put(uuid, session);
        writes.submit(uuid, () -> repository.all(uuid), loaded -> {
            if (!player.isOnline() || revisions.get(uuid) != session) {
                return;
            }
            cache.load(uuid, loaded);
            Bukkit.getPluginManager().callEvent(new PlayerSettingsLoadedEvent(player));
        }, () -> {
            if (player.isOnline()) {
                player.sendMessage(BlockcraftApi.get().messages().get("operation.failed"));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.forget(event.getPlayer().getUniqueId());
        revisions.remove(event.getPlayer().getUniqueId());
    }
}
