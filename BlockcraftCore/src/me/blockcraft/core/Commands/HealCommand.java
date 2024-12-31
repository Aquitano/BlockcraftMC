package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HealCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.heal")) {
            p.setFoodLevel(20);
            p.setHealth(20.0);
            p.sendMessage(Main.Prefix + "\u00a77Du wurdest geheilt!");
        }
        return true;
    }
}
