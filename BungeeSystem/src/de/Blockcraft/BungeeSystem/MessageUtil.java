package de.Blockcraft.BungeeSystem;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import static de.Blockcraft.BungeeSystem.Data.PREFIX;

public class MessageUtil {

    public static void sendPlayerMessage(ProxiedPlayer player, String message) {
        player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void sendPlayerMessage(CommandSender player, String message) {
        player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void sendPlayerMessage(CommandSender player, String prefix, String message) {
        player.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void sendPlayerMessage(ProxiedPlayer player, String prefix, String message) {
        player.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }
}
