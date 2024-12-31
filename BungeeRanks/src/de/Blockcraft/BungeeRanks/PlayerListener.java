package de.Blockcraft.BungeeRanks;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class PlayerListener
        implements Listener {
    public static final ConcurrentMap<ProxiedPlayer, String> prefixes = new ConcurrentHashMap<>();
    public static ArrayList<ProxiedPlayer> list = new ArrayList<>();
    private final Plugin plugin;

    public PlayerListener(Plugin pl) {
        this.plugin = pl;
    }

    @EventHandler
    public void onEvent(PermissionCheckEvent event) {
        event.setHasPermission(BungeeRanks.hasPermissions(event.getSender().getName(), event.getPermission()));
    }

    @EventHandler
    public void onEvent(final ServerConnectedEvent event) {
        ProxyServer.getInstance().getScheduler().schedule(this.plugin, () -> BungeeRanks.updateBukkitPermissions(event.getPlayer()), 5, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onEvent(ServerDisconnectEvent event) {
        prefixes.remove(event.getPlayer());
    }

    @EventHandler
    public void onEvent(ServerSwitchEvent event) {
        prefixes.remove(event.getPlayer());
    }

    @EventHandler
    public void onEvent(PostLoginEvent event) {
        BungeeRanks.updatePlayerName(event.getPlayer());

        // Init player in database
        String test = BungeeRanks.playerExists(String.valueOf(event.getPlayer()));
        if (test == null) {
            String playerName = event.getPlayer().getName();
            BungeeRanks.createPlayer(BungeeRanks.getUUID(playerName), playerName);
        }
    }

}

