package me.blockcraft.api.world;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Who may change the map right now. {@code /build} toggles it and it ends with the session, on every server alike. */
public final class BuildMode implements Listener {

    private final Set<UUID> builders = new HashSet<>();

    public boolean isEnabled(Player player) {
        return builders.contains(player.getUniqueId());
    }

    /** Returns whether build mode is enabled after the toggle. */
    public boolean toggle(Player player) {
        if (builders.remove(player.getUniqueId())) {
            return false;
        }
        builders.add(player.getUniqueId());
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        builders.remove(event.getPlayer().getUniqueId());
    }
}
