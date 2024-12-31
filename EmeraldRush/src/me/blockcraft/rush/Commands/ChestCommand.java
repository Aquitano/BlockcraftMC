package me.blockcraft.rush.Commands;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.Shop.ChainArmor2;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class ChestCommand implements Listener, CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0) {
            if (Main.State_Shopping) {
                if (Main.online.contains(p)) {
                    ChainArmor2.openInventory(p);
                }
            } else {
                p.sendMessage(Main.Prefix + "\u00a7cDu kannst diesen Command nur in der Shopping-Phase benutzen!!");
            }
        }
        return false;
    }
}
