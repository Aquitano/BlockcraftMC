package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SudoCommand implements CommandExecutor {
    String message;

    public SudoCommand() {
        this.message = "";
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.sudo")) {
            if (args.length == 0 || args.length == 1) {
                p.sendMessage(Main.Prefix + "\u00a7cBenutze /sudo <name>");
            }
            if (args.length >= 2) {
                final Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    if (!target.isOp() && !target.getName().equalsIgnoreCase("Aquitano")) {
                        for (int i = 1; i < args.length; ++i) {
                            this.message = this.message + args[i] + " ";
                        }
                        p.sendMessage(Main.Prefix + "\u00a77Der Spieler \u00a7f" + target.getName() + " \u00a77gibt automatisch \u00a7f" + this.message + "\u00a77ein!");
                        target.chat(this.message);
                        this.message = "";
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDer Sudo Command ist für diesen Spieler nicht erlabt!");
                    }
                } else {
                    p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
                }
            }
        }
        return true;
    }
}
