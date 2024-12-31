package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodeCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.gamemode")) {
            p.sendMessage(Main.Prefix + "\u00a7cBenutze /gm [1,2,3]");
        }
        if (args.length == 1 && p.hasPermission("bs.gamemode")) {
            if (args[0].equalsIgnoreCase("0")) {
                p.setGameMode(GameMode.SURVIVAL);
                p.sendMessage(Main.Prefix + "\u00a77Dein Gamemode wurde auf \u00a7fSurvival \u00a77gesetz!");
            } else if (args[0].equalsIgnoreCase("1")) {
                p.setGameMode(GameMode.CREATIVE);
                p.sendMessage(Main.Prefix + "\u00a77Dein Gamemode wurde auf \u00a7fCreative \u00a77gesetz!");
            } else if (args[0].equalsIgnoreCase("2")) {
                p.setGameMode(GameMode.ADVENTURE);
                p.sendMessage(Main.Prefix + "\u00a77Dein Gamemode wurde auf \u00a7fAdventure \u00a77gesetz!");
            } else if (args[0].equalsIgnoreCase("3")) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(Main.Prefix + "\u00a77Dein Gamemode wurde auf \u00a7fSpectator \u00a77gesetz!");
            }
        }
        return true;
    }
}
