package me.blockcraft.reportsmenu.listeners;

import me.blockcraft.reportsmenu.Main;
import me.blockcraft.reportsmenu.util.ReportsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListeners implements Listener {
    private final Main plugin;

    public JoinListeners(final Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (p.hasPermission("reports.menu")) {
            final Integer anz = ReportsManager.getReportAnz();
            if (anz > 0) {
                p.sendMessage(this.plugin.prefix + "Es gibt aktuell §c" + anz + " §7Reports!");
            }
        }
    }
}
