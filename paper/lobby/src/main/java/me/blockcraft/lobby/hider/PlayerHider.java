package me.blockcraft.lobby.hider;

import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Lets a player hide every other player, with a short cooldown between toggles; the choice survives a relog. */
public final class PlayerHider {

    private final Plugin plugin;
    private final PlayerSettings settings;
    private final int cooldownTicks;
    private final Set<UUID> hiding = new HashSet<>();

    public PlayerHider(Plugin plugin, PlayerSettings settings, long cooldownMillis) {
        this.plugin = plugin;
        this.settings = settings;
        cooldownTicks = Math.toIntExact((cooldownMillis + 49) / 50);
    }

    public boolean isHiding(Player player) {
        return hiding.contains(player.getUniqueId());
    }

    public boolean onCooldown(Player player) {
        return player.hasCooldown(Material.LIME_DYE) || player.hasCooldown(Material.GRAY_DYE);
    }

    public void hideAll(Player player) {
        settings.set(player, PlayerSetting.PLAYER_VISIBILITY, PlayerSetting.OFF);
    }

    public void showAll(Player player) {
        settings.set(player, PlayerSetting.PLAYER_VISIBILITY, PlayerSetting.ON);
    }

    public void applyStored(Player player) {
        if (settings.enabled(player, PlayerSetting.PLAYER_VISIBILITY)) {
            if (hiding.remove(player.getUniqueId())) {
                Bukkit.getOnlinePlayers().forEach(other -> player.showPlayer(plugin, other));
            }
        } else {
            hide(player);
        }
    }

    public int cooldownTicks() {
        return cooldownTicks;
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
    }

    private void hide(Player player) {
        if (!hiding.add(player.getUniqueId())) {
            return;
        }
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player))
                .forEach(other -> player.hidePlayer(plugin, other));
    }
}
