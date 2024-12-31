package me.blockcraft.QuickSG.Commands;

import me.blockcraft.QuickSG.LocationAPI;
import me.blockcraft.QuickSG.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class SetupCommands
        implements Listener,
        CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player p = (Player) sender;
        if (args.length == 1 && p.isOp()) {
            if (args[0].equalsIgnoreCase("help")) {
                p.sendMessage("");
                p.sendMessage("\u00a77/set lobby,Spectator,Pos1(-8),DM1(-4),Lobbymitte,InGamemitte");
                p.sendMessage("");
            } else if (args[0].equalsIgnoreCase("lobby")) {
                LocationAPI.setLocation("Lobby", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Lobby \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("spectator")) {
                LocationAPI.setLocation("Spectator", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Spectator \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos1")) {
                LocationAPI.setLocation("Pos1", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos1 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos2")) {
                LocationAPI.setLocation("Pos2", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos2 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos3")) {
                LocationAPI.setLocation("Pos3", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos3 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos4")) {
                LocationAPI.setLocation("Pos4", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos4 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos5")) {
                LocationAPI.setLocation("Pos5", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos5 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos6")) {
                LocationAPI.setLocation("Pos6", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos6 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos7")) {
                LocationAPI.setLocation("Pos7", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos7 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Pos8")) {
                LocationAPI.setLocation("Pos8", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Pos8 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM1")) {
                LocationAPI.setLocation("DM1", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM1 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM2")) {
                LocationAPI.setLocation("DM2", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM2 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM3")) {
                LocationAPI.setLocation("DM3", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Dm3 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("DM4")) {
                LocationAPI.setLocation("DM4", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f DM4 \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("Lobbymitte")) {
                LocationAPI.setLocation("Lobbymitte", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f Lobbymitte \u00a77gesetzt!");
            } else if (args[0].equalsIgnoreCase("InGamemitte")) {
                LocationAPI.setLocation("InGamemitte", p);
                p.sendMessage(Main.Prefix + "\u00a77Du hast die Location\u00a7f InGamemitte \u00a77gesetzt!");
            }
        }
        return true;
    }
}

