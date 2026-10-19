package me.blockcraft.reports.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

final class ReportListMenu extends ListMenu<Report> {

    private static final int ROWS = 6;
    private static final int TARGET_SLOT = (ROWS - 1) * 9;

    private final ReportMenus menus;
    private final Messages messages;
    private final Optional<String> reported;

    ReportListMenu(ReportMenus menus, Messages messages, List<Report> reports, Optional<String> reported) {
        super(ROWS, messages.get(reported.isPresent() ? "menu.title-player" : "menu.title-all"));
        this.menus = menus;
        this.messages = messages;
        this.reported = reported;
        reported.ifPresent(name -> info(TARGET_SLOT, Items.head(name)
                .name(messages.get("menu.target", Placeholder.unparsed("player", name))).build()));
        closeButton();
        show(reports);
    }

    @Override
    protected ItemStack icon(Report report) {
        var placeholders = ReportMenus.placeholders(messages, report);
        return Items.head(report.reported())
                .name(messages.get("menu.entry", placeholders))
                .lore(messages.getLines("menu.entry-lore", placeholders))
                .build();
    }

    @Override
    protected void onSelect(Player player, Report report, InventoryClickEvent event) {
        menus.openReport(player, report, reported);
    }
}
