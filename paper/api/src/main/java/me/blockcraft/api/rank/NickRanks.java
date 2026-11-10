package me.blockcraft.api.rank;

import me.blockcraft.api.nick.NickChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The LuckPerms group a nicked player shows instead of their own, so a nicked admin reads as a player wherever a
 * rank is drawn. Recorded at the lowest priority, so every other listener of {@link NickChangeEvent} already sees
 * the new rank through {@link Ranks#prefix}.
 */
public final class NickRanks implements Listener {

    private final Map<UUID, String> groups = new ConcurrentHashMap<>();

    public Optional<String> of(Player player) {
        return Optional.ofNullable(groups.get(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNickChange(NickChangeEvent event) {
        var id = event.getPlayer().getUniqueId();
        event.rankGroup().ifPresentOrElse(group -> groups.put(id, group), () -> groups.remove(id));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        groups.remove(event.getPlayer().getUniqueId());
    }
}
