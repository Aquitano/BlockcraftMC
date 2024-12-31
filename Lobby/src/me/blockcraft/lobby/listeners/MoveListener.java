package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import me.blockcraft.lobby.utils.Particles;
import me.eventseen.Data.Data;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Random;

public class MoveListener implements Listener {
    public static Lobby main;

    public MoveListener(final Lobby main) {
        MoveListener.main = main;
    }

    @EventHandler
    public void onMoves(final PlayerMoveEvent e) {
        final Player p = e.getPlayer();
        final Location loc = e.getPlayer().getLocation();
        final Block b = p.getWorld().getBlockAt(loc);
        if (b.getType() == Material.IRON_PLATE) {
            if (!p.hasPermission("bs.premium")) {
                p.teleport(MoveListener.main.getSpawn());
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dafür benötigst du \u00a76Premium\u00a77!");
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Mehr erfahren \u00a7e/Premium");
            } else {
                p.teleport(MoveListener.main.getPremium());
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du betrittst den \u00a76Premium \u00a77Bereich.");
            }
        }
        if (b.getType() != Material.STONE_PLATE) {
            return;
        }
        if (Lobby.Feuerwerkuser.contains(p)) {
            return;
        }
        final World world = p.getLocation().getWorld();
        final double x = -65.0;
        final double y = 22.5;
        final double z = 56.0;
        final Location loc2 = new Location(world, x, y, z);
        final Firework fw = (Firework) p.getWorld().spawnEntity(loc2, EntityType.FIREWORK);
        final FireworkMeta fwm = fw.getFireworkMeta();
        final Random r = new Random();
        final int rt = r.nextInt(4) + 1;
        FireworkEffect.Type type = FireworkEffect.Type.BALL;
        if (rt == 2) {
            type = FireworkEffect.Type.BALL_LARGE;
        }
        if (rt == 3) {
            type = FireworkEffect.Type.BURST;
        }
        if (rt == 4) {
            type = FireworkEffect.Type.CREEPER;
        }
        final int r1i = r.nextInt(17) + 1;
        final int r2i = r.nextInt(17) + 1;
        final Color c1 = this.getColor(r1i);
        final Color c2 = this.getColor(r2i);
        final FireworkEffect effect = FireworkEffect.builder().flicker(r.nextBoolean()).withColor(c1).withFade(c2).with(type).trail(r.nextBoolean()).build();
        fwm.addEffect(effect);
        final int rp = r.nextInt(2) + 1;
        fwm.setPower(rp);
        fw.setFireworkMeta(fwm);
        final World world2 = p.getLocation().getWorld();
        final double x2 = -69.0;
        final double y2 = 22.5;
        final double z2 = 56.0;
        final Location loc3 = new Location(world2, x2, y2, z2);
        final Firework fw2 = (Firework) p.getWorld().spawnEntity(loc3, EntityType.FIREWORK);
        final FireworkMeta fwm2 = fw2.getFireworkMeta();
        final Random r2 = new Random();
        final int rt2 = r.nextInt(4) + 1;
        FireworkEffect.Type type2 = FireworkEffect.Type.BALL;
        if (rt2 == 2) {
            type2 = FireworkEffect.Type.BALL_LARGE;
        }
        if (rt2 == 3) {
            type2 = FireworkEffect.Type.BURST;
        }
        if (rt2 == 4) {
            type2 = FireworkEffect.Type.CREEPER;
        }
        final int r1i2 = r2.nextInt(17) + 1;
        final int r2i2 = r2.nextInt(17) + 1;
        final Color c3 = this.getColor(r1i2);
        final Color c4 = this.getColor(r2i2);
        final FireworkEffect effect2 = FireworkEffect.builder().flicker(r2.nextBoolean()).withColor(c3).withFade(c4).with(type2).trail(r2.nextBoolean()).build();
        fwm2.addEffect(effect2);
        final int rp2 = r2.nextInt(2) + 1;
        fwm2.setPower(rp2);
        fw2.setFireworkMeta(fwm2);
        Lobby.Feuerwerkuser.add(p);
    }

    private Color getColor(final int i) {
        Color c = null;
        if (i == 1) {
            c = Color.AQUA;
        }
        if (i == 2) {
            c = Color.BLACK;
        }
        if (i == 3) {
            c = Color.BLUE;
        }
        if (i == 4) {
            c = Color.FUCHSIA;
        }
        if (i == 5) {
            c = Color.GRAY;
        }
        if (i == 6) {
            c = Color.GREEN;
        }
        if (i == 7) {
            c = Color.LIME;
        }
        if (i == 8) {
            c = Color.MAROON;
        }
        if (i == 9) {
            c = Color.NAVY;
        }
        if (i == 10) {
            c = Color.OLIVE;
        }
        if (i == 11) {
            c = Color.ORANGE;
        }
        if (i == 12) {
            c = Color.PURPLE;
        }
        if (i == 13) {
            c = Color.RED;
        }
        if (i == 14) {
            c = Color.SILVER;
        }
        if (i == 15) {
            c = Color.TEAL;
        }
        if (i == 16) {
            c = Color.WHITE;
        }
        if (i == 17) {
            c = Color.YELLOW;
        }
        return c;
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onMoveAdmin(final PlayerMoveEvent e) {
        final Player p = e.getPlayer();
        final Location loc = e.getPlayer().getLocation();
        final Block b = p.getWorld().getBlockAt(loc);
        if (Lobby.Zauberei.contains(p)) {
            final Particles packet = new Particles(EnumParticle.SPELL_WITCH, p.getLocation(), 1.0f, 1.0f, 1.0f, 0.05f, 1);
            packet.sendToPlayer(p);
        } else if (Lobby.Ender.contains(p)) {
            final Particles packet = new Particles(EnumParticle.NOTE, p.getLocation(), 1.0f, 1.0f, 1.0f, 0.05f, 1);
            packet.sendToPlayer(p);
        } else if (Lobby.Feuer.contains(p)) {
            final Particles packet = new Particles(EnumParticle.FLAME, p.getLocation(), 1.0f, 1.0f, 1.0f, 0.05f, 1);
            packet.sendToPlayer(p);
        } else if (Lobby.Feuerwerk.contains(p)) {
            final Particles packet = new Particles(EnumParticle.FIREWORKS_SPARK, p.getLocation(), 1.0f, 1.0f, 1.0f, 0.05f, 1);
            packet.sendToPlayer(p);
        }
    }
}
