package me.blockcraft.lobby.hider;

import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lets a player hide every other player, with a short cooldown between toggles; the choice survives a relog. */
public final class PlayerHider {

    private final Plugin plugin;
    private final PlayerSettings settings;
    private final long cooldownMillis;
    private final Set<UUID> hiding = new HashSet<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();

    public PlayerHider(Plugin plugin, PlayerSettings settings, long cooldownMillis) {
        this.plugin = plugin;
        this.settings = settings;
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
        settings.set(player, PlayerSetting.PLAYER_VISIBILITY, PlayerSetting.OFF);
        hide(player);
    }

    public void showAll(Player player) {
        lastToggle.put(player.getUniqueId(), System.currentTimeMillis());
        settings.set(player, PlayerSetting.PLAYER_VISIBILITY, PlayerSetting.ON);
        hiding.remove(player.getUniqueId());
        Bukkit.getOnlinePlayers().forEach(other -> player.showPlayer(plugin, other));
    }

    /** Re-applies a stored "players hidden" once the settings arrived; true when the player is hidden now. */
    public boolean applyStored(Player player) {
        if (settings.enabled(player, PlayerSetting.PLAYER_VISIBILITY)) {
            return false;
        }
        hide(player);
        return true;
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

    private void hide(Player player) {
        hiding.add(player.getUniqueId());
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player))
                .forEach(other -> player.hidePlayer(plugin, other));
    }
}
