package me.blockcraft.reports.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentCatalogRepository.Entry;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** The /strafe catalogue as buttons; a click runs the numbered punishment against the reported player on the proxy. */
final class PunishMenu extends Menu {

    private static final int ENTRY_SLOTS = 45;
    private static final int BACK = 45;
    private static final int CLOSE = 53;

    private final ReportMenus menus;
    private final Report report;
    private final Optional<String> reported;
    private final List<Entry> shown;

    PunishMenu(ReportMenus menus, Messages messages, Report report, Optional<String> reported, List<Entry> catalog) {
        super(6, messages.get("menu.title-punish", ReportMenus.placeholders(report)));
        this.menus = menus;
        this.report = report;
        this.reported = reported;
        this.shown = catalog.subList(0, Math.min(catalog.size(), ENTRY_SLOTS));

        for (int slot = 0; slot < shown.size(); slot++) {
            var entry = shown.get(slot);
            var placeholders = TagResolver.resolver(ReportMenus.placeholders(report),
                    Formatter.number("number", entry.number()),
                    Placeholder.unparsed("reason", entry.reason()),
                    Placeholder.component("type", messages.get("menu.punish-type." + entry.type().name().toLowerCase())));
            set(slot, Items.of(entry.type() == PunishmentType.BAN ? Material.BARRIER : Material.PAPER,
                            Math.clamp(entry.number(), 1, 64))
                    .name(messages.get("menu.punish-entry", placeholders))
                    .lore(messages.getLines("menu.punish-entry-lore", placeholders))
                    .build());
        }
        set(BACK, Items.of(Material.ARROW).name(messages.get("menu.back")).lore(messages.get("menu.back-lore")).build());
        set(CLOSE, Items.of(Material.BARRIER).name(messages.get("menu.close")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot < shown.size()) {
            menus.punish(player, report, shown.get(slot));
        } else if (slot == BACK) {
            menus.openReport(player, report, reported);
        } else if (slot == CLOSE) {
            close(player);
        }
    }
}
