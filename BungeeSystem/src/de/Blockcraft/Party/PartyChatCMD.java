package de.Blockcraft.Party;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class PartyChatCMD extends Command {
    public PartyChatCMD() {
        super("pc");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(PartyCMD.PREFIX + ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return;
        }

        String playerName = player.getName();

        if (Party.isInParty(playerName)) {
            if (args.length == 0) {
                player.sendMessage(PartyCMD.PREFIX + ChatColor.YELLOW + "Bitte gib eine Nachricht ein, um sie an deine Party zu senden.");
                return;
            }

            String message = String.join(" ", args).trim();

            if (message.isEmpty()) {
                player.sendMessage(PartyCMD.PREFIX + ChatColor.YELLOW + "Die Nachricht darf nicht leer sein.");
                return;
            }

            Party.partyChat(player, message);
        } else {
            player.sendMessage(PartyCMD.PREFIX + ChatColor.YELLOW + "Du befindest dich in keiner " + ChatColor.AQUA + "Party" + ChatColor.YELLOW + ".");
        }
    }
}