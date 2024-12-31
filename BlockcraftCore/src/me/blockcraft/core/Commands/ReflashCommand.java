package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class ReflashCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player player = (Player) sender;
        if (args.length == 0 && player.hasPermission("bs.reflash")) {
            final EntityPlayer p = ((CraftPlayer) player).getHandle();
            for (final Player players : Bukkit.getOnlinePlayers()) {
                final PlayerConnection connection = ((CraftPlayer) players).getHandle().playerConnection;
                connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, p));
                connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, p));
            }
            player.sendMessage(Main.Prefix + "\u00a77Alle Spieler müssten nun sichtbar sein!");
        }
        return false;
    }
}
