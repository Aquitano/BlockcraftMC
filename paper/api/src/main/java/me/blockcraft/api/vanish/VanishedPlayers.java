package me.blockcraft.api.vanish;

import me.blockcraft.common.moderation.VanishRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is vanished on this server, already while their join is handled: core hides a vanished joiner only at the
 * end of the join event, after the games have seated them, so the flag is read from {@code bc_vanished} during
 * the async pre-login and follows every {@link VanishChangeEvent} from then on.
 */
public final class VanishedPlayers implements Listener {

    private final VanishRepository repository;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishedPlayers(VanishRepository repository) {
        this.repository = repository;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (repository.isVanished(event.getUniqueId())) {
            vanished.add(event.getUniqueId());
        } else {
            vanished.remove(event.getUniqueId());
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
