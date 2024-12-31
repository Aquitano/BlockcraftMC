package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.tp")) {
            p.sendMessage(Main.Prefix + "\u00a7cBenutze /tp [Name] [Name]");
        }
        if (args.length == 1 && p.hasPermission("bs.tp")) {
            final Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                p.sendMessage(Main.Prefix + "\u00a77Du hast dich zu \u00a7f" + target.getName() + "\u00a77 teleportiert!");
                p.teleport(target);
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
            }
        }
        if (args.length == 2 && p.hasPermission("bs.tp")) {
            final Player zu = Bukkit.getPlayer(args[1]);
            final Player von = Bukkit.getPlayer(args[0]);
            if (zu != null || von != null) {
                assert zu != null;
                p.sendMessage(Main.Prefix + "\u00a77Du hast \u00a7f" + von.getName() + "\u00a77 zu \u00a7f" + zu.getName() + "\u00a77 teleportiert!");
                von.teleport(zu);
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cEiner Dieser Spieler ist nicht online!");
            }
        }
        return false;
    }
}
