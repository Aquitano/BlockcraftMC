package me.blockcraft.rush.tools;

import me.blockcraft.coins.CAPI;
import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.StatsAPI;
import me.blockcraft.rush.Scoreboards.Scoreboard_Ending;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.meta.FireworkMeta;

public class OnlineAbfrage implements Listener {
    public static boolean endet;

    static {
        OnlineAbfrage.endet = false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void abfragen(final Player p) {
        if (!Main.State_Lobby && (Main.online.size() == 1 || Main.online.isEmpty()) && !OnlineAbfrage.endet) {
            OnlineAbfrage.endet = true;
            for (final Player winner : Main.online) {
                Bukkit.broadcastMessage(Main.Prefix + "\u00a77Der Spieler \u00a7f" + winner.getName() + "\u00a77 hat die Runde gewonnen!");
                StatsAPI.setWins(winner.getUniqueId().toString(), StatsAPI.getWins(winner.getUniqueId().toString()) + 1, winner.getName());
                CAPI.addCoins(winner.getUniqueId().toString(), 15);
                StatsAPI.setCoins(winner.getUniqueId().toString(), StatsAPI.getCoins(winner.getUniqueId().toString()) + 15, winner.getName());
                final Firework firework = (Firework) winner.getWorld().spawn(winner.getLocation(), (Class) Firework.class);
                final FireworkEffect effect = FireworkEffect.builder().withColor(Color.RED).withFade(Color.BLUE).with(FireworkEffect.Type.BALL_LARGE).build();
                final FireworkMeta fireworkmeta = firework.getFireworkMeta();
                fireworkmeta.addEffect(effect);
                fireworkmeta.setPower(1);
                firework.setFireworkMeta(fireworkmeta);
            }
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Ending.add(all);
            }
            Restart.start(p);
        }
    }
}
