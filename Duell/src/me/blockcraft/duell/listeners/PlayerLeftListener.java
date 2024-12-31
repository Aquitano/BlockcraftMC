package me.blockcraft.duell.listeners;

import me.blockcraft.duell.Duell;
import me.blockcraft.duell.methods.BoardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerLeftListener implements Listener {
    public static Duell main;

    public PlayerLeftListener(final Duell main) {
        PlayerLeftListener.main = main;
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        final Player p = e.getPlayer();
        if (PlayerLeftListener.main.Lobby) {
            if (PlayerLeftListener.main.full) {
                if (Duell.online.isEmpty()) {
                    PlayerLeftListener.main.vs = false;
                    PlayerLeftListener.main.SG = false;
                    PlayerLeftListener.main.SJ = false;
                    PlayerLeftListener.main.voted = false;
                    PlayerLeftListener.main.motd = true;
                    PlayerLeftListener.main.full = false;
                }
                return;
            }
            PlayerLeftListener.main.vs = false;
            PlayerLeftListener.main.SG = false;
            PlayerLeftListener.main.SJ = false;
            PlayerLeftListener.main.voted = false;
            PlayerLeftListener.main.motd = true;
            for (final Player all : Duell.online) {
                if (!Duell.online.isEmpty()) {
                    BoardManager.sendInGameBoard(all);
                }
            }
            PlayerLeftListener.main.full = false;
        }
    }
}
