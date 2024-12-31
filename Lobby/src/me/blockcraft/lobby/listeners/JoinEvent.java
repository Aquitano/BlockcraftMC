package me.blockcraft.lobby.listeners;

import me.blockcraft.coins.CAPI;
import me.blockcraft.lobby.APIs.TitlesAPI;
import me.blockcraft.lobby.Lobby;
import me.blockcraft.lobby.utils.Board;
import me.blockcraft.lobby.utils.Inventar;
import me.eventseen.Data.Data;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.inventivetalent.bossbar.BossBar;
import org.inventivetalent.bossbar.BossBarAPI;

public class JoinEvent implements Listener {
    public static Lobby main;

    public JoinEvent(final Lobby main) {
        JoinEvent.main = main;
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        e.setJoinMessage("");
        for (int i = 0; i <= 120; ++i) {
            p.sendMessage("");
        }
        p.setGameMode(GameMode.ADVENTURE);
        TitlesAPI.sendTitle(p, 10, 20, 10, "\u00a79" + Data.BLOCKCRAFT_LABEL, "\u00a77Willkommen!");
        final int coins = CAPI.getCoins(p.getUniqueId().toString());
        p.setLevel(coins);
        Inventar.setupStandardItems(p);
        p.teleport(JoinEvent.main.getSpawn());
        p.spigot().setCollidesWithEntities(false);
        final BossBar boss = BossBarAPI.addBar(p, new TextComponent("\u00a78● \u00a77Du spielst auf \u00a79play." + Data.BLOCKCRAFT_LABEL + "\u00a77! \u00a78●"), BossBarAPI.Color.BLUE, BossBarAPI.Style.NOTCHED_10, 1.0f, 20, 2L);
        for (final Player all : Bukkit.getOnlinePlayers()) {
            Board.sendBoard(all);
        }
    }
}
