package me.blockcraft.lobby.utils;

import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class Particles {
    private final PacketPlayOutWorldParticles packet;

    public Particles(final EnumParticle type, final Location loc, final float xOffset, final float yOffset, final float zOffset, final float speed, final int count) {
        final float x = (float) loc.getX();
        final float y = (float) loc.getY();
        final float z = (float) loc.getZ();
        this.packet = new PacketPlayOutWorldParticles(type, true, x, y, z, xOffset, yOffset, zOffset, speed, count, (int[]) null);
    }

    public void sendToAll() {
        for (final Player p : Bukkit.getServer().getOnlinePlayers()) {
            ((CraftPlayer) p).getHandle().playerConnection.sendPacket(this.packet);
        }
    }

    public void sendToPlayer(final Player p) {
        ((CraftPlayer) p).getHandle().playerConnection.sendPacket(this.packet);
    }
}
