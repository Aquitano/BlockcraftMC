package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHealthCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 1 && p.hasPermission("bs.sethealth")) {
            p.sendMessage(Main.Prefix + "\u00a7cbenutze /sethealth [Name]");
        }
        if (args.length == 2 && p.hasPermission("bs.sethealth")) {
            final Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                try {
                    target.setMaxHealth(Integer.parseInt(args[1]));
                    target.setHealth(Integer.parseInt(args[1]));
                    p.sendMessage(Main.Prefix + "\u00a7f" + target.getName() + "\u00a77 hat nun \u00a7f" + args[1] + "\u00a77 Herzen!");
                } catch (Exception e) {
                    p.sendMessage(Main.Prefix + "\u00a7cDu musst eine Zahl angeben!");
                }
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
            }
        }
        return false;
    }
}
