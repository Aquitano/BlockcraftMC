package me.blockcraft.lobby.commands;

import me.blockcraft.lobby.Lobby;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandGlobalMute implements CommandExecutor {
    public static Lobby main;

    public CommandGlobalMute(final Lobby main) {
        CommandGlobalMute.main = main;
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String alias, final String[] args) {
        if (cmd.getName().equalsIgnoreCase("globalmute") && sender.hasPermission("bs.mute")) {
            if (CommandGlobalMute.main.mute) {
                Bukkit.broadcastMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Der Chat wurde entstummt.");
                CommandGlobalMute.main.mute = false;
            } else {
                Bukkit.broadcastMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Der Chat wurde gestummt.");
                CommandGlobalMute.main.mute = true;
            }
        }
        return false;
    }
}
