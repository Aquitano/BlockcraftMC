package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class VanishCommand implements CommandExecutor {
    public final ArrayList<Player> vanish;

    public VanishCommand() {
        this.vanish = new ArrayList<>();
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.vanish")) {
            if (this.vanish.contains(p)) {
                this.vanish.remove(p);
                p.sendMessage(Main.Prefix + "\u00a77Du bist nun wieder sichtbar!");
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.showPlayer(p);
                }
            } else {
                this.vanish.add(p);
                p.sendMessage(Main.Prefix + "\u00a77Du bist nun unsichtbar!");
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    all.hidePlayer(p);
                }
            }
        }
        return false;
    }
}
