package me.blockcraft.reports.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

final class ReportMenu extends Menu {

    private static final int GOTO_REPORTER = 1;
    private static final int GOTO_REPORTED = 7;
    private static final int CLAIM = 29;
    private static final int SET_STATUS = 31;
    private static final int PUNISH = 40;
    private static final int BACK = 45;
    private static final int DELETE = 49;
    private static final int CLOSE = 53;

    private final ReportMenus menus;
    private final Report report;
    private final Optional<String> reported;

    ReportMenu(ReportMenus menus, Messages messages, Report report, Optional<String> reported, String staff) {
        super(6, messages.get("menu.title-report", ReportMenus.placeholders(messages, report)));
        this.menus = menus;
        this.report = report;
        this.reported = reported;

        TagResolver placeholders = ReportMenus.placeholders(messages, report);
        set(GOTO_REPORTER, item(Material.ENDER_PEARL, "menu.goto-reporter", messages, placeholders));
        set(GOTO_REPORTED, item(Material.ENDER_PEARL, "menu.goto-reported", messages, placeholders));
        set(3, Items.head(report.reporter()).name(messages.get("menu.reporter"))
                .lore(messages.get("menu.reporter-lore", placeholders)).build());
        set(4, Items.of(Material.NAME_TAG).name(messages.get("menu.id", placeholders))
                .lore(messages.getLines("menu.id-lore", placeholders)).build());
        set(5, Items.head(report.reported()).name(messages.get("menu.reported"))
                .lore(messages.get("menu.reported-lore", placeholders)).build());
        set(20, item(Material.COMMAND_BLOCK, "menu.server", messages, placeholders));
        set(22, item(Material.CLOCK, "menu.status", messages, placeholders));
        set(24, item(Material.BOOK, "menu.reason", messages, placeholders));
        set(CLAIM, claim(messages, report, staff, placeholders));
        set(SET_STATUS, item(Material.NAME_TAG, "menu.set-status", messages, placeholders));
        set(PUNISH, item(Material.IRON_AXE, "menu.punish", messages, placeholders));
        set(DELETE, item(Material.REDSTONE_BLOCK, "menu.delete", messages, placeholders));
        backButton(BACK, viewer -> menus.openList(viewer, reported, 1));
        closeButton(CLOSE);
    }

    private static ItemStack item(Material material, String key, Messages messages, TagResolver placeholders) {
        return Items.of(material).name(messages.get(key, placeholders)).lore(messages.get(key + "-lore", placeholders)).build();
    }

    /** A report somebody else holds wears their head, so a second moderator sees it before clicking anything. */
    private static ItemStack claim(Messages messages, Report report, String staff, TagResolver placeholders) {
        if (report.isClaimedBy(staff)) {
            return item(Material.ORANGE_DYE, "menu.release", messages, placeholders);
        }
        return report.claimedBy()
                .map(holder -> Items.head(holder).name(messages.get("menu.claimed", placeholders))
                        .lore(messages.get("menu.claimed-lore", placeholders)).build())
                .orElseGet(() -> item(Material.LIME_DYE, "menu.claim", messages, placeholders));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case GOTO_REPORTER -> menus.goTo(player, report.reporter());
            case GOTO_REPORTED -> menus.goTo(player, report.reported());
            case CLAIM -> menus.toggleClaim(player, report, reported);
            case SET_STATUS -> menus.promptStatus(player, report, reported);
            case PUNISH -> menus.openPunish(player, report, reported);
            case DELETE -> menus.delete(player, report, reported);
            default -> {
            }
        }
    }
}
