package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Manager_Chat;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class TeamChat
        extends Command {
    public static final String PREFIX = ChatColor.RED + "TeamChat " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;


    public TeamChat() {
        super("tc");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            MessageUtil.sendPlayerMessage(sender, Data.PLAYER_ONLY);
            return;
        }

        if (player.hasPermission("bs.teamchat")) {
            if (args.length == 0) {
                player.sendMessage(PREFIX + "\u00a77Im TeamChat sind gerade diese Spieler:");
                for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
                    if (!p.hasPermission("bs.teamchat")) continue;
                    player.sendMessage(ChatColor.GRAY + "  \u00a77 - \u00a7c" + p.getDisplayName() + "\u00a77 -\u00a7c " + p.getServer().getInfo().getName());
                }
                player.sendMessage(PREFIX + "\u00a77Verwende bitte" + ChatColor.AQUA + "/tc <message>" + ChatColor.GOLD + " \u00a77um eine Nachticht an alle Team-Mitglieder zu senden.");
            } else {
                StringBuilder message = new StringBuilder();
                int count = 0;
                while (count < args.length) {
                    message.append(" ").append(args[count]);
                    count = count + 1;
                }
                if ((message = new StringBuilder(Manager_Chat.getMessage(player, message.toString()))) == null) {
                    return;
                }
                message.insert(0, PREFIX + ChatColor.AQUA + player.getName() + ChatColor.RESET + " (" + player.getServer().getInfo().getName() + "): " + ChatColor.BLUE);
                System.out.println(message);
                for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
                    if (!p.hasPermission("bs.teamchat")) continue;
                    p.sendMessage(message.toString());
                }
            }
        } else {
            MessageUtil.sendPlayerMessage(player, Data.NO_PERM);
        }
    }
}

