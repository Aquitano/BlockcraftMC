package me.blockcraft.rush.Commands;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.StatsAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class StatsCommand implements Listener, CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0) {
            p.sendMessage("");
            p.sendMessage("\u00a78» \u00a79Wins: \u00a77" + StatsAPI.getWins(p.getUniqueId().toString()));
            p.sendMessage("\u00a78» \u00a79Loses: \u00a77" + StatsAPI.getLoses(p.getUniqueId().toString()));
            p.sendMessage("\u00a78» \u00a79Deaths: \u00a77" + StatsAPI.getDeaths(p.getUniqueId().toString()));
            p.sendMessage("\u00a78» \u00a79Kills: \u00a77" + StatsAPI.getKills(p.getUniqueId().toString()));
            p.sendMessage("\u00a78» \u00a79TW Points: \u00a77" + StatsAPI.getCoins(p.getUniqueId().toString()));
            p.sendMessage("");
        }
        if (args.length == 1) {
            final Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(Main.Prefix + "\u00a7cDieser Spieler ist nicht online!");
            } else {
                p.sendMessage("");
                p.sendMessage("\u00a77" + target.getName() + "`s Stats");
                p.sendMessage("\u00a78» \u00a79Wins:\u00a77 " + StatsAPI.getWins(target.getUniqueId().toString()));
                p.sendMessage("\u00a78» \u00a79Loses: \u00a77" + StatsAPI.getLoses(target.getUniqueId().toString()));
                p.sendMessage("\u00a78» \u00a79Deaths: \u00a77" + StatsAPI.getDeaths(target.getUniqueId().toString()));
                p.sendMessage("\u00a78» \u00a79Kills: \u00a77" + StatsAPI.getKills(target.getUniqueId().toString()));
                p.sendMessage("\u00a78» \u00a79TW Points: \u00a77" + StatsAPI.getCoins(target.getUniqueId().toString()));
                p.sendMessage("");
            }
        }
        return false;
    }
}
