package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SunCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.sun")) {
            p.getLocation().getWorld().setStorm(false);
            p.sendMessage(Main.Prefix + "\u00a77Du hast den Regen \u00a7fdeaktiviert\u00a77!");
        }
        return false;
    }
}
