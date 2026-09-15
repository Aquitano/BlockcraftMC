package me.blockcraft.reports;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentCatalogRepository;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.reports.command.ReportsCommand;
import me.blockcraft.reports.listener.JoinListener;
import me.blockcraft.reports.menu.ReportMenus;
import org.bukkit.plugin.java.JavaPlugin;


public final class ReportsPlugin extends JavaPlugin {

    public static final String MENU_PERMISSION = "blockcraft.reports.menu";

    @Override
    public void onEnable() {
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), BlockcraftApi.get().tags());
        var reports = new ReportRepository(BlockcraftApi.get().database());
        reports.createTable();
        var catalog = new PunishmentCatalogRepository(BlockcraftApi.get().database());
        catalog.createTable();

        var menus = new ReportMenus(this, reports, catalog, messages);
        getServer().getPluginManager().registerEvents(new JoinListener(this, reports, messages), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new CommandRegistry(event.registrar(), messages).register(ReportsCommand.create(menus, messages)));
    }
}
