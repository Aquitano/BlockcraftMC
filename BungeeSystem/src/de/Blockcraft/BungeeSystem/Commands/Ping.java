package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class Ping
        extends Command {
    public Ping() {
        super("ping");
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer p)) {
            sender.sendMessage("Du bist kein Spieler!");
            return;
        }

        MessageUtil.sendPlayerMessage(p, "\u00a77Dein Ping beträgt: \u00a7a" + p.getPing() + "\u00a77ms.");
    }
}

