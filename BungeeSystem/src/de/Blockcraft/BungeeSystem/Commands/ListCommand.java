package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class ListCommand
        extends Command {
    public ListCommand() {
        super("list");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        ProxiedPlayer p = (ProxiedPlayer) sender;
        p.sendMessage("\u00a77\u00a7l\u00a7m------------  \u00a7e\u00a7l" + Data.IP + " Netzwerk \u00a78\u00a7l- \u00a77\u00a7lList \u00a77\u00a7l\u00a7m------------");
        p.sendMessage(" ");
        p.sendMessage("\u00a77Spieler auf dem Netzwerk: \u00a7a" + BungeeCord.getInstance().getOnlineCount());
        p.sendMessage("\u00a77Spieler auf \u00a7c" + p.getServer().getInfo().getName() + "\u00a77:\u00a7a " + p.getServer().getInfo().getPlayers().size());
        p.sendMessage(" ");
        p.sendMessage("\u00a77\u00a7l\u00a7m------------  \u00a7e\u00a7l" + Data.IP + " Netzwerk \u00a78\u00a7l- \u00a77\u00a7lList \u00a77\u00a7l\u00a7m------------");
    }
}

