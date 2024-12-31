package me.blockcraft.rush.Commands;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class SetupCommands implements Listener, CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 1 && p.isOp()) {
            if (args[0].equalsIgnoreCase("help")) {
                p.sendMessage("");
                p.sendMessage("\u00a77/twset lobby,Spectator,such,DM1(-6),Enchant,shopping");
                p.sendMessage("");
            } else if (args[0].equalsIgnoreCase("lobby")) {
                RushManager.setLocation("Lobby", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Lobby \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("spectator")) {
                RushManager.setLocation("Spectator", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Spectator \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("such")) {
                RushManager.setLocation("Such", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Such \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM1")) {
                RushManager.setLocation("DM1", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM1 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM2")) {
                RushManager.setLocation("DM2", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM2 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM3")) {
                RushManager.setLocation("DM3", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM3 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM4")) {
                RushManager.setLocation("DM4", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM4 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM5")) {
                RushManager.setLocation("DM5", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM5 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM6")) {
                RushManager.setLocation("DM6", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM6 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Enchant")) {
                RushManager.setLocation("Enchant", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Enchant \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("shopping")) {
                RushManager.setLocation("shopping", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f shopping \u00a77gesetzt!");
            }
        }
        return false;
    }
}
