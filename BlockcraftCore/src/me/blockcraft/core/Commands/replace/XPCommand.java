package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class XPCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.xp")) {
            p.sendMessage(Main.Prefix + "\u00a7cBenutze /xp [Name] [Level]");
        }
        if (args.length == 2 && p.hasPermission("bs.xp")) {
            final Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                p.sendMessage(Main.Prefix + "\u00a77Du hast den Spieler \u00a7f" + target.getName() + " " + args[1] + "\u00a77 Level gegeben!");
                target.setLevel(Integer.parseInt(args[1]));
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
            }
        }
        return false;
    }
}
