package me.blockcraft.lobby.hider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lets a player hide every other player, with a short cooldown between toggles. */
public final class PlayerHider {

    private final Plugin plugin;
    private final long cooldownMillis;
    private final Set<UUID> hiding = new HashSet<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();

    public PlayerHider(Plugin plugin, long cooldownMillis) {
        this.plugin = plugin;
        this.cooldownMillis = cooldownMillis;
    }

    public boolean isHiding(Player player) {
        return hiding.contains(player.getUniqueId());
    }

    public boolean onCooldown(Player player) {
        Long last = lastToggle.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldownMillis;
    }

    public void hideAll(Player player) {
        lastToggle.put(player.getUniqueId(), System.currentTimeMillis());
        hiding.add(player.getUniqueId());
        Bukkit.getOnlinePlayers().forEach(other -> player.hidePlayer(plugin, other));
    }

    public void showAll(Player player) {
        lastToggle.put(player.getUniqueId(), System.currentTimeMillis());
        hiding.remove(player.getUniqueId());
        Bukkit.getOnlinePlayers().forEach(other -> player.showPlayer(plugin, other));
    }

    /** Keeps a freshly joined player invisible to everyone who has the hider on. */
    public void onJoin(Player joined) {
        for (UUID id : hiding) {
            Player hider = Bukkit.getPlayer(id);
            if (hider != null && !hider.equals(joined)) {
                hider.hidePlayer(plugin, joined);
            }
        }
    }

    public void forget(Player player) {
        hiding.remove(player.getUniqueId());
        lastToggle.remove(player.getUniqueId());
    }
}
