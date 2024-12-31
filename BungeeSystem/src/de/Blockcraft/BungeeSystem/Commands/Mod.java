package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class Mod extends Command {
    public Mod() {
        super("mod");
    }

    public void execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof ProxiedPlayer p)) {
            MessageUtil.sendPlayerMessage(sender, Data.PLAYER_ONLY);
            return;
        }
        if (p.hasPermission("bs.team")) {
            int i = 0;
            for (final ProxiedPlayer pp : BungeeCord.getInstance().getPlayers()) {
                if (pp.hasPermission("bs.team")) {
                    ++i;
                }
            }
            MessageUtil.sendPlayerMessage(p, "§7Es sind §e" + i + " §7Teammitglieder online.");
        }
    }
}