package me.blockcraft.reports;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.reports.command.ReportsCommand;
import me.blockcraft.reports.listener.ChatPromptListener;
import me.blockcraft.reports.listener.JoinListener;
import me.blockcraft.reports.menu.ReportMenus;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ReportsPlugin extends JavaPlugin {

    public static final String MENU_PERMISSION = "blockcraft.reports.menu";

    @Override
    public void onEnable() {
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        var reports = new ReportRepository(BlockcraftApi.get().database());
        reports.createTable();

        var menus = new ReportMenus(this, reports, messages);
        getServer().getPluginManager().registerEvents(new JoinListener(this, reports, messages), this);
        getServer().getPluginManager().registerEvents(new ChatPromptListener(menus), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ReportsCommand.create(menus, messages), "Öffnet das Reports-Menü", List.of()));
    }
}
