package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.ArrayList;
import java.util.Collection;

public class Find extends Command {

    public Find() {
        super("find");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bs.find")) {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
            return;
        }

        if (args.length == 0) {
            MessageUtil.sendPlayerMessage(sender, "Syntax: /find <Spieler>");
            return;
        }
        Collection<ProxiedPlayer> playerscol = BungeeCord.getInstance().getPlayers();
        ArrayList<ProxiedPlayer> players = new ArrayList<>(playerscol);
        if (players.contains(BungeeCord.getInstance().getPlayer(args[0]))) {
            ProxiedPlayer pr = BungeeCord.getInstance().getPlayer(args[0]);
            MessageUtil.sendPlayerMessage(sender, "\u00a7aDer Spieler befindet sich auf dem Server \u00a73" + pr.getServer().getInfo().getName() + "\u00a7a.");
        } else {
            MessageUtil.sendPlayerMessage(sender, "\u00a7eDer Spieler ist nicht online.");
        }
    }
}
