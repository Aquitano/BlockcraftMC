package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class Broadcast
        extends Command {
    public Broadcast() {
        super("bc");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.broadcast")) {
            if (args.length == 0) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Verwende \u00a7c/bc <Nachricht>");
            } else {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                while (i < args.length) {
                    sb.append(args[i]).append(" ");
                    ++i;
                }
                String st = sb.toString();
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage("");
                    all.sendMessage("\u00a7c" + Data.BLOCKCAST_PREFIX + " \u00a78\u00a7l•» \u00a77" + st.replace("&", "\u00a7"));
                    all.sendMessage("");
                }
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

