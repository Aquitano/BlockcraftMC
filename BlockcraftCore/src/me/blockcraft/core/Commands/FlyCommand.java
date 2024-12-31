package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class FlyCommand implements CommandExecutor {
    public static ArrayList<Player> fly;

    static {
        FlyCommand.fly = new ArrayList<>();
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length != 0) {
            return false;
        }
        if (!p.hasPermission("bs.fly")) {
            return true;
        }
        if (FlyCommand.fly.contains(p)) {
            FlyCommand.fly.remove(p);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.sendMessage(Main.Prefix + "\u00a77Du kannst nun \u00a7fnicht \u00a77mehr fliegen!");
            return true;
        }
        FlyCommand.fly.add(p);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.sendMessage(Main.Prefix + "\u00a77Du kannst nun fliegen!");
        return true;
    }
}
