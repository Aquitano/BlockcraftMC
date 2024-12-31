package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TimeCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.time")) {
            p.sendMessage(Main.Prefix + "\u00a7cBenutze /time [tag,nach,10...]");
        }
        if (args.length != 1) {
            return false;
        }
        if (!p.hasPermission("bs.time")) {
            return true;
        }
        if (args[0].equalsIgnoreCase("day") | args[0].equalsIgnoreCase("tag")) {
            p.getLocation().getWorld().setTime(6000L);
            p.sendMessage(Main.Prefix + "\u00a77Du hast die Zeit zu \u00a7fTag\u00a77 geändert!");
            return true;
        }
        if (args[0].equalsIgnoreCase("night") | args[0].equalsIgnoreCase("nacht")) {
            p.getLocation().getWorld().setTime(18000L);
            p.sendMessage(Main.Prefix + "\u00a77Du hast die Zeit zu \u00a7fNacht\u00a77 geändert!");
            return true;
        }
        int i;
        try {
            i = Integer.parseInt(args[0]);
        } catch (NumberFormatException x) {
            p.sendMessage(Main.Prefix + "\u00a7cNutze für die Zeit \u00a7fday\u00a7c, \u00a7fnight\u00a7c oder einen \u00a7fZahlenwert\u00a7c!");
            return true;
        }
        p.getLocation().getWorld().setTime(i);
        p.sendMessage(Main.Prefix + "\u00a77Du hast die Zeit auf \u00a7f" + i + "\u00a77 gesetzt!");
        return true;
    }
}
