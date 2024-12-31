package me.blockcraft.core.Commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ServerConnectCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.join")) {
            if (args.length == 0) {
                p.sendMessage(Main.Prefix + "\u00a7cBenutze /connect [ServerName]");
            }
            if (args.length == 1) {
                p.sendMessage(Main.Prefix + "\u00a77Du betrittst den Server \u00a7f" + args[0]);
                final ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(args[0]);
                p.sendPluginMessage(Main.getInstance(), "BungeeCord", out.toByteArray());
            }
        }
        return false;
    }
}
