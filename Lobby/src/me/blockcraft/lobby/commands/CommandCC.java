package me.blockcraft.lobby.commands;

import me.blockcraft.lobby.Lobby;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandCC implements CommandExecutor {
    public static Lobby main;

    public CommandCC(final Lobby main) {
        CommandCC.main = main;
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String alias, final String[] args) {
        final Player p = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("cc") && p.hasPermission("bs.cc")) {
            for (final Player ps : Bukkit.getOnlinePlayers()) {
                if (ps.hasPermission("bs.cc")) {
                    ps.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Der Chat wurde von \u00a7c" + p.getName() + "\u00a77 geleert.");
                } else {
                    for (int i = 0; i <= 120; ++i) {
                        ps.sendMessage("");
                    }
                    ps.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Der Chat wurde von \u00a7c" + p.getName() + "\u00a77 geleert.");
                }
            }
        }
        return false;
    }
}
