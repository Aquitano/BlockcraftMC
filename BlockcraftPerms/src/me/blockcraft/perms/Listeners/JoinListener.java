package me.blockcraft.perms.Listeners;

import me.blockcraft.perms.MySQL.API;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

public class JoinListener implements Listener {
    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        String prefix = "\u00a77";
        String displayName = "\u00a77SPIELER \u00a78● \u00a77" + p.getName();
        String rank = "";

        if (API.isOwner(p)) {
            prefix = "\u00a7b"; // Aqua for Owner
            displayName = "\u00a7bOWNER \u00a78● \u00a7b" + p.getName();
            rank = "owner";
        } else if (API.isAdmin(p)) {
            prefix = "\u00a74"; // Red for Admin
            displayName = "\u00a74ADMIN \u00a78● \u00a74" + p.getName();
            rank = "admin";
        } else if (API.isDev(p)) {
            prefix = "\u00a7b"; // Aqua for Dev
            displayName = "\u00a7bDEV \u00a78● \u00a7b" + p.getName();
            rank = "dev";
        } else if (API.isYT(p)) {
            prefix = "\u00a75"; // Purple for YouTuber
            displayName = "\u00a75YT \u00a78● \u00a75" + p.getName();
            rank = "yt";
        } else if (API.isPremium(p)) {
            prefix = "\u00a76"; // Gold for Premium
            displayName = "\u00a76PREMIUM \u00a78● \u00a76" + p.getName();
            rank = "premium";
        }

        if (!rank.isEmpty()) {
            API.assignLuckPermsGroup(p.getUniqueId(), rank);
        }

        p.setPlayerListName(prefix + p.getName());
        p.setDisplayName(displayName);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(final PlayerLoginEvent event) {
        if (!event.getAddress().getHostAddress().equals("127.0.0.1") && !event.getHostname().equals("24.ip.gl.joinmc.link:51647")) {
            System.out.println("Blocked connection from " + event.getAddress().getHostAddress());
            System.out.println("Blocked connection from " + event.getHostname());
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "\u00a7cDu musst über den Proxy connecten");
        }
    }
}

