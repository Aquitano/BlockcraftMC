package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public class MSGCommand implements CommandExecutor {
    String message;

    public MSGCommand() {
        this.message = "";
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.msg")) {
            if (args.length == 0 || args.length == 1) {
                p.sendMessage(Main.Prefix + "\u00a7cBenutze /msg <name>");
            }
            if (args.length >= 2) {
                final Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    if (!Objects.equals(target.getName(), p.getName())) {
                        for (int i = 1; i < args.length; ++i) {
                            this.message = this.message + args[i] + " ";
                        }
                        target.playSound(target.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                        target.sendMessage("\u00a7f" + p.getPlayerListName() + " \u00a7a» " + target.getPlayerListName() + "\u00a77: \u00a7b" + this.message.replace("&", "\u00a7"));
                        p.sendMessage("\u00a7f" + p.getPlayerListName() + " \u00a7c« " + target.getPlayerListName() + "\u00a77: \u00a7b" + this.message.replace("&", "\u00a7"));
                        this.message = "";
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu kannst dir nicht selber schreiben!");
                    }
                } else {
                    p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
                }
            }
        }
        return true;
    }
}
