package me.blockcraft.reports.listener;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.reports.ReportsPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class JoinListener implements Listener {

    private final Plugin plugin;
    private final ReportRepository reports;
    private final Messages messages;

    public JoinListener(Plugin plugin, ReportRepository reports, Messages messages) {
        this.plugin = plugin;
        this.reports = reports;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!player.hasPermission(ReportsPlugin.MENU_PERMISSION)) {
            return;
        }
        Tasks.async(plugin, () -> {
            int count = reports.count();
            if (count > 0) {
                player.sendMessage(messages.get(count == 1 ? "join-one" : "join-many", Formatter.number("count", count)));
            }
        });
    }
}
