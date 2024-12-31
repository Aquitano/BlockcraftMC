package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RainCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.rain")) {
            p.getLocation().getWorld().setStorm(true);
            p.sendMessage(Main.Prefix + "\u00a77Du hast den Regen \u00a7faktiviert\u00a77!");
        }
        return false;
    }
}
