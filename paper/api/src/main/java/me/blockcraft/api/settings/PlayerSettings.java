package me.blockcraft.api.settings;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.Database;
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
 * {@code bc_player_settings}, cached per online player so games can read a choice on the main thread mid-fight.
 * The cache fills right after the join event; a read before that is empty, not wrong.
 */
public final class PlayerSettings implements Listener {

    private final Plugin plugin;
    private final Database db;
    private final Map<UUID, Map<PlayerSetting, String>> cache = new ConcurrentHashMap<>();

    public PlayerSettings(Plugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_player_settings ("
                + "player_uuid CHAR(36) NOT NULL, "
                + "setting VARCHAR(32) NOT NULL, "
                + "value VARCHAR(64) NOT NULL, "
                + "PRIMARY KEY (player_uuid, setting))");
    }

    public Optional<String> get(Player player, PlayerSetting setting) {
        return Optional.ofNullable(cache.getOrDefault(player.getUniqueId(), Map.of()).get(setting));
    }

    public void set(Player player, PlayerSetting setting, String value) {
        cache.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(PlayerSetting.class)).put(setting, value);
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> db.update("INSERT INTO bc_player_settings (player_uuid, setting, value) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE value = VALUES(value)", uuid.toString(), setting.key(), value));
    }

    public void clear(Player player, PlayerSetting setting) {
        cache.getOrDefault(player.getUniqueId(), Map.of()).remove(setting);
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> db.update("DELETE FROM bc_player_settings WHERE player_uuid = ? AND setting = ?",
                uuid.toString(), setting.key()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        Tasks.async(plugin, () -> load(uuid), loaded -> cache.merge(uuid, loaded, (fresh, stored) -> {
            stored.putAll(fresh);
            return stored;
        }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private Map<PlayerSetting, String> load(UUID uuid) {
        var loaded = new EnumMap<PlayerSetting, String>(PlayerSetting.class);
        for (var setting : PlayerSetting.values()) {
            db.queryOne("SELECT value FROM bc_player_settings WHERE player_uuid = ? AND setting = ?",
                    row -> row.getString("value"), uuid.toString(), setting.key()).ifPresent(value -> loaded.put(setting, value));
        }
        return loaded;
    }
}
