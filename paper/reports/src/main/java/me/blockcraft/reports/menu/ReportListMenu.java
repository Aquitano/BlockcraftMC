package me.blockcraft.reports.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

final class ReportListMenu extends ListMenu<Report> {

    private static final int ROWS = 6;

    private final ReportMenus menus;
    private final Messages messages;
    private final Optional<String> reported;

    ReportListMenu(ReportMenus menus, Messages messages, List<Report> reports, Optional<String> reported) {
        super(ROWS, messages.get(reported.isPresent() ? "menu.title-player" : "menu.title-all"));
        this.menus = menus;
        this.messages = messages;
        this.reported = reported;
        reported.ifPresent(name -> info(lastRowSlot() + 1, Items.head(name)
                .name(messages.get("menu.target", Placeholder.unparsed("player", name))).build()));
        refreshButton();
        closeButton();
        show(reports);
    }

    @Override
    protected ItemStack icon(Report report) {
        var placeholders = ReportMenus.placeholders(messages, report);
        var lore = new ArrayList<>(messages.getLines("menu.entry-lore", placeholders));
        lore.add(messages.get("menu.open"));
        return Items.head(report.reported())
                .name(messages.get("menu.entry", placeholders))
                .lore(lore)
                .build();
    }

    @Override
    protected Component empty() {
        return reported.map(name -> messages.get("menu.empty-player", Placeholder.unparsed("player", name)))
                .orElseGet(() -> messages.get("menu.empty"));
    }

    @Override
    protected void reload(Player player) {
        loading();
        menus.reloadList(player, reported, this::show, this::failed);
    }

    @Override
    protected void onSelect(Player player, Report report, InventoryClickEvent event) {
        menus.openReport(player, report, reported);
    }
}
