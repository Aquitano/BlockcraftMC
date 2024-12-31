package me.blockcraft.rush.Commands;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinsCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 3 && (p.hasPermission("bs.startGame")) && args[0].equalsIgnoreCase("set")) {
            final Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                RushManager.setCoins(target, Integer.parseInt(args[2]));
                p.sendMessage(Main.Prefix + "\u00a77Du hast diesem Spieler \u00a7f" + args[2] + " \u00a77Emeralds gegeben!");
            } else {
                p.sendMessage(Main.Prefix + "\u00a77Dieser Spieler ist nicht online!");
            }
        }
        return false;
    }
}
