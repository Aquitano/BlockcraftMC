package me.blockcraft.perms.Methods;

import me.blockcraft.perms.MySQL.API;
import org.bukkit.entity.Player;

public class Manager {
    public static void refreshPlayer(final Player p) {
        String name = p.getName();
        String prefix = "\u00a77";
        String displayName = "\u00a77SPIELER \u00a78● \u00a77" + name;

        if (API.isOwner(p)) {
            prefix = "\u00a7b"; // Aqua for Owner
            displayName = "\u00a7bOWNER \u00a78● \u00a7b" + name;
        } else if (API.isAdmin(p)) {
            prefix = "\u00a74"; // Red for Admin
            displayName = "\u00a74ADMIN \u00a78● \u00a74" + name;
        } else if (API.isDev(p)) {
            prefix = "\u00a7b"; // Aqua for Dev
            displayName = "\u00a7bDEV \u00a78● \u00a7b" + name;
        } else if (API.isYT(p)) {
            prefix = "\u00a75"; // Purple for YouTuber
            displayName = "\u00a75YT \u00a78● \u00a75" + name;
        } else if (API.isPremium(p)) {
            prefix = "\u00a76"; // Gold for Premium
            displayName = "\u00a76PREMIUM \u00a78● \u00a76" + name;
        }

        p.setPlayerListName(prefix + name);
        p.setDisplayName(displayName);
    }
}
