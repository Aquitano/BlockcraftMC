package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class Online extends Command {
    public Online() {
        super("online");
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer p)) {
            MessageUtil.sendPlayerMessage(sender, Data.PLAYER_ONLY);
            return;
        }

        MessageUtil.sendPlayerMessage(p, "§7Es sind momentan §e" + BungeeCord.getInstance().getOnlineCount() + " §7Spieler online.");
    }
}
