package me.blockcraft.rush.Commands;

import me.blockcraft.rush.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class BuildCommand implements Listener, CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("server.build")) {
            if (Main.builder.contains(p)) {
                Main.builder.remove(p);
                p.sendMessage(Main.Prefix + "\u00a77Build Modus \u00a7fdeaktiviert!");
            } else {
                Main.builder.add(p);
                p.sendMessage(Main.Prefix + "\u00a77Build Modus \u00a7faktivert!");
            }
        }
        return false;
    }
}
