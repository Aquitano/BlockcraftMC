package me.blockcraft.rush.Commands;

import me.blockcraft.coins.CAPI;
import me.blockcraft.rush.Main;
import me.blockcraft.rush.Shop.ChainArmor2;
import me.blockcraft.rush.tools.Timer_Shopping;
import me.blockcraft.rush.tools.Timer_SuchZeit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MainCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 1 && p.hasPermission("b.admin")) {
            if (args[0].equalsIgnoreCase("endsuch")) {
                Timer_SuchZeit.i = 10;
            } else if (args[0].equalsIgnoreCase("endshop")) {
                Timer_Shopping.i = 10;
            } else if (args[0].equalsIgnoreCase("shop")) {
                ChainArmor2.openInventory(p);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("emeralds")) {
            final Player target = Bukkit.getPlayer(args[1]);
            CAPI.addCoins(target.getUniqueId().toString(), Integer.parseInt(args[2]));
            p.sendMessage(Main.Prefix + "\u00a77Du hast diesem Spieler \u00a7f" + args[2] + "\u00a77 Emeralds gegeben!");
        }
        return false;
    }
}
