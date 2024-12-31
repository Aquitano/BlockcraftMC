package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class SkullCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.skull")) {
            if (args.length == 1) {
                final ItemStack skull = new ItemStack(Material.SKULL_ITEM);
                skull.setDurability((short) 3);
                final SkullMeta meta = (SkullMeta) skull.getItemMeta();
                meta.setOwner(args[0]);
                skull.setItemMeta(meta);
                p.getInventory().addItem(skull);
                p.sendMessage(Main.Prefix + "\u00a77Du hast den Kopf von \u00a7f" + args[0] + "\u00a77 erhalten!");
            } else {
                p.sendMessage(Main.Prefix + "\u00a77Benutze \u00a7f/skull [name]");
            }
        }
        return true;
    }
}
