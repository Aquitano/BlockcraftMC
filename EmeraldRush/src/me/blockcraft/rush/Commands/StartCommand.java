package me.blockcraft.rush.Commands;

import me.blockcraft.coins.CAPI;
import me.blockcraft.rush.Listener.PlayerJoin;
import me.blockcraft.rush.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;

public class StartCommand implements Listener, CommandExecutor {
    static HashMap<Integer, String> rang;

    static {
        StartCommand.rang = new HashMap<>();
    }

    public int countdown;

    public StartCommand() {
        this.countdown = 6;
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.startGame")) {
            if (Main.online.size() == 2 || Main.online.size() > 1) {
                if (Main.State_Lobby) {
                    if (PlayerJoin.i > 5) {
                        PlayerJoin.i = 6;
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDie Runde wurde bereits gestartet!");
                    }
                } else {
                    p.sendMessage(Main.Prefix + "\u00a7cDu kannst die Runde nur im Lobby Modus starten!");
                }
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cEs müssen mindesten 2 Spieler Online sein!");
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("coins") && p.isOp()) {
            final Player target = Bukkit.getPlayer(args[1]);
            CAPI.addCoins(target.getUniqueId().toString(), 200);
            p.sendMessage(Main.Prefix + "Du hast diesem Spieler 200 Coins hinzugefügt!");
        }
        return false;
    }
}
