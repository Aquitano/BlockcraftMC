package me.blockcraft.api.vanish;

import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.VanishRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Who is vanished on this server, already while their join is handled: core hides a vanished joiner only at the
 * end of the join event, after the games have seated them, so the flag is read from {@code bc_vanished} during
 * the async pre-login and follows every {@link VanishChangeEvent} from then on. A vanished staffer must never
 * arrive visible, so a failed read turns the login away.
 */
public final class VanishedPlayers implements Listener {

    private final Plugin plugin;
    private final VanishRepository repository;
    private final Messages messages;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishedPlayers(Plugin plugin, VanishRepository repository, Messages messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            if (repository.isVanished(event.getUniqueId())) {
                vanished.add(event.getUniqueId());
            } else {
                vanished.remove(event.getUniqueId());
            }
        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the vanish state of " + event.getName() + ", turning the login away", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("login.retry"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVanishChange(VanishChangeEvent event) {
        if (event.isVanished()) {
            vanished.add(event.getPlayer().getUniqueId());
        } else {
            vanished.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        vanished.remove(event.getPlayer().getUniqueId());
    }
}
