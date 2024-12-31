package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class Invsee implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        //if (!(sender instanceof Player p)) {
        if (!(sender.getClass().isInstance(Player.class))) {
            sender.sendMessage(Main.Prefix + "\u00a7cDu musst ein Spieler sein um diesen Befehl zu nutzen!");
            return true;
        }

        final Player p = (Player) sender;

        if (!p.hasPermission("bs.invsee")) {
            if (args.length == 1) {
                final Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
                    return true;
                }
                final Inventory inv = target.getInventory();
                p.closeInventory();
                p.openInventory(inv);
                p.sendMessage(Main.Prefix + "Du siehst nun das Inventar von \u00a7f" + target.getName());
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cBeNutze /invsee [Spieler]");
            }
        }
        return true;
    }
}
