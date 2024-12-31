package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ServerInfoCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.serverinfo") && args.length == 0) {
            final Runtime runtime = Runtime.getRuntime();
            p.sendMessage("");
            p.sendMessage(Main.Prefix + "\u00a77Maximaler Ram: \u00a7f" + runtime.maxMemory() / 1048576L + "\u00a77 MB");
            final long free = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
            p.sendMessage(Main.Prefix + "\u00a77Benutzter Ram: \u00a7f" + free / 1048576L + "\u00a77 MB");
            p.sendMessage("");
        }
        return true;
    }
}
