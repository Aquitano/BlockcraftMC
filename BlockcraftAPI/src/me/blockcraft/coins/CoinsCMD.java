package me.blockcraft.coins;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinsCMD implements CommandExecutor {
    private final String PREFIX = ChatColor.GOLD + "Blockcraft " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;

    public CoinsCMD() {
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, String s, String[] strings) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Du musst ein Spieler sein!");
            return false;
        }

        Player p = (Player) sender;
        int coins = CAPI.getCoins(p.getUniqueId().toString());
        p.sendMessage(PREFIX + "\u00a77Du hast\u00a7e " + coins + " \u00a77Coins!");
        return true;
    }
}
