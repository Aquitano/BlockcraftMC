package me.blockcraft.system;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.settings.PlayerSettingRepository;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player lets the network do to them. The Paper servers own {@code bc_player_settings} and report every
 * change over the plugin channel; the table is read once at login so a player who never opens a menu is still
 * judged on their stored choices. An unread setting counts as on, like everywhere else.
 */
public final class PlayerChoices {

    private final PlayerSettingRepository repository;
    private final Logger logger;
    private final Map<UUID, Map<PlayerSetting, String>> choices = new ConcurrentHashMap<>();

    public PlayerChoices(PlayerSettingRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        return EventTask.async(() -> {
            try {
                choices.merge(player, new ConcurrentHashMap<>(repository.all(player)), (live, stored) -> {
                    stored.putAll(live);
                    return stored;
                });
            } catch (DatabaseException e) {
                logger.warn("Could not read the settings of {}", player, e);
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        choices.remove(event.getPlayer().getUniqueId());
    }

    public boolean enabled(Player player, PlayerSetting setting) {
        return enabled(player.getUniqueId(), setting);
    }

    /** Reads the table for a player who is not online, so a friend request judges an absent player correctly. */
    public boolean enabled(UUID player, PlayerSetting setting) {
        var cached = choices.get(player);
        return PlayerSetting.enabled(cached == null
                ? repository.get(player, setting)
                : Optional.ofNullable(cached.get(setting)));
    }

    public void set(Player player, PlayerSetting setting, String value) {
        choices.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(setting, value);
    }
}
