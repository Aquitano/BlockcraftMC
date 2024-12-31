package de.Blockcraft.HubAPI;

import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.logging.Level;

public class Hub {
    public static void send(CommandSender sender) {
        if (!(sender instanceof ProxiedPlayer player)) {
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Du bist kein Spieler!");
            return;
        }

        String selectedServerName = SelectLobby.select();
        if (selectedServerName == null || selectedServerName.isEmpty()) {
            MessageUtil.sendPlayerMessage(player, ChatColor.RED + "Derzeit sind keine Lobby-Server verfügbar.");
            return;
        }

        ServerInfo targetServer = BungeeCord.getInstance().getServerInfo(selectedServerName);
        if (targetServer == null) {
            MessageUtil.sendPlayerMessage(player, ChatColor.RED + "Der ausgewählte Server ist nicht verfügbar.");
            return;
        }

        ServerInfo currentServer = player.getServer() != null ? player.getServer().getInfo() : null;
        if (currentServer != null && currentServer.equals(targetServer)) {
            MessageUtil.sendPlayerMessage(player, ChatColor.RED + "Du bist bereits auf der Lobby.");
            return;
        }

        try {
            MessageUtil.sendPlayerMessage(player, ChatColor.GREEN + "Du wirst zur " + ChatColor.AQUA + "Lobby " + ChatColor.GREEN + "verbunden.");
            player.connect(targetServer);
        } catch (Exception e) {
            MessageUtil.sendPlayerMessage(player, ChatColor.RED + "Der Server ist nicht verfügbar.");
            BungeeCord.getInstance().getLogger().log(Level.SEVERE, "Fehler beim Verbinden des Spielers " + player.getName() + " zur Lobby: ", e);
        }
    }
}