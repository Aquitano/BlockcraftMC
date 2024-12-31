package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FullFoodCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.feed")) {
            if (args.length == 0) {
                p.setFoodLevel(20);
                p.sendMessage(Main.Prefix + "\u00a77Du hast nun keinen Hunger mehr!");
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cNutze /feed");
            }
        }
        return true;
    }
}
