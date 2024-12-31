package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.HubAPI.ServerSend;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.ArrayList;
import java.util.Collection;

public class Goto extends Command {

    public Goto() {
        super("goto");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bs.goto")) {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
            return;
        }

        if (!(sender instanceof ProxiedPlayer send)) {
            MessageUtil.sendPlayerMessage(sender, Data.PLAYER_ONLY);
            return;
        }

        if (args.length == 0) {
            MessageUtil.sendPlayerMessage(sender, "Syntax: /goto <Spieler>");
            return;
        }
        Collection<ProxiedPlayer> playerscol = BungeeCord.getInstance().getPlayers();
        ArrayList<ProxiedPlayer> players = new ArrayList<>(playerscol);
        if (players.contains(BungeeCord.getInstance().getPlayer(args[0]))) {
            ServerSend.send(send, BungeeCord.getInstance().getPlayer(args[0]).getServer().getInfo());
        } else {
            MessageUtil.sendPlayerMessage(sender, "\u00a7eDer Spieler ist nicht online.");
        }


    }


}
