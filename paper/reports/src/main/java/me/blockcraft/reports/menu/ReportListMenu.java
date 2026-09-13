package me.blockcraft.reports.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

final class ReportListMenu extends Menu {

    private static final int PAGE_SIZE = 36;
    private static final int PREVIOUS = 48;
    private static final int NEXT = 50;
    private static final int CLOSE = 53;

    private final ReportMenus menus;
    private final Messages messages;
    private final List<Report> all;
    private final List<Report> shown;
    private final Optional<String> reported;
    private final int page;
    private final int pages;

    ReportListMenu(ReportMenus menus, Messages messages, List<Report> all, Optional<String> reported, int page) {
        super(6, messages.get(reported.isPresent() ? "menu.title-player" : "menu.title-all"));
        this.menus = menus;
        this.messages = messages;
        this.all = all;
        this.reported = reported;
        this.pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.clamp(page, 1, pages);
        this.shown = all.subList((this.page - 1) * PAGE_SIZE, Math.min(this.page * PAGE_SIZE, all.size()));

        for (int slot = 0; slot < shown.size(); slot++) {
            var report = shown.get(slot);
            var placeholders = ReportMenus.placeholders(report);
            set(slot, Items.head(report.reported())
                    .name(messages.get("menu.entry", placeholders))
                    .lore(messages.getLines("menu.entry-lore", placeholders))
                    .build());
        }
        reported.ifPresent(name -> set(45, Items.head(name)
                .name(messages.get("menu.target", Placeholder.unparsed("player", name))).build()));
        set(49, Items.of(Material.BOOK, Math.min(this.page, 64)).name(messages.get("menu.page"))
                .lore(messages.get("menu.page-lore", Formatter.number("page", this.page))).build());
        if (this.page > 1) {
            set(PREVIOUS, Items.of(Material.PAPER).name(messages.get("menu.previous")).lore(messages.get("menu.previous-lore")).build());
        }
        if (this.page < pages) {
            set(NEXT, Items.of(Material.PAPER).name(messages.get("menu.next")).lore(messages.get("menu.next-lore")).build());
        }
        closeButton(CLOSE);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot < shown.size()) {
            menus.openReport(player, shown.get(slot), reported);
        } else if (slot == PREVIOUS && page > 1) {
            new ReportListMenu(menus, messages, all, reported, page - 1).open(player);
        } else if (slot == NEXT && page < pages) {
            new ReportListMenu(menus, messages, all, reported, page + 1).open(player);
        }
    }
}
