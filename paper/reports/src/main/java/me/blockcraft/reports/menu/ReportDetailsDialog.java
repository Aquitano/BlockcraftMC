package me.blockcraft.reports.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** One report and what staff can do about it; the exit returns to the list it was opened from. */
final class ReportDetailsDialog {

    private final Dialog dialog;

    ReportDetailsDialog(ReportMenus menus, Messages messages, Report report, Optional<String> reported, Player staff) {
        var placeholders = ReportMenus.placeholders(messages, report);
        var buttons = new ArrayList<ActionButton>();
        if (staff.hasPermission("blockcraft.system.goto")) {
            buttons.add(button(messages, "menu.goto-reporter", placeholders, viewer -> menus.goTo(viewer, report.reporter())));
            buttons.add(button(messages, "menu.goto-reported", placeholders, viewer -> menus.goTo(viewer, report.reported())));
        }
        var claim = claim(report, staff.getUniqueId(), BlockcraftApi.get().realName(staff));
        if (!claim.equals("menu.claimed")) {
            buttons.add(button(messages, claim, placeholders, viewer -> menus.toggleClaim(viewer, report, reported)));
        }
        buttons.add(button(messages, "menu.set-status", placeholders, viewer -> menus.promptStatus(viewer, report, reported)));
        if (ReportMenus.canPunish(staff, PunishmentType.BAN) || ReportMenus.canPunish(staff, PunishmentType.MUTE)) {
            buttons.add(button(messages, "menu.punish", placeholders, viewer -> menus.openPunish(viewer, report, reported)));
        }
        buttons.add(button(messages, "menu.resolve", placeholders, viewer -> menus.resolve(viewer, report, reported)));
        dialog = Dialogs.create(messages.get("menu.title-report", placeholders),
                List.of(
                        DialogBody.item(Items.head(report.reported()).build())
                                .description(DialogBody.plainMessage(messages.get("menu.accused", placeholders)))
                                .showTooltip(false)
                                .build(),
                        DialogBody.plainMessage(Component.join(JoinConfiguration.newlines(),
                                messages.getLines("menu.entry-lore", placeholders)))),
                List.of(),
                Dialogs.actions(buttons, Dialogs.button(BlockcraftApi.get().messages().get("menu.back"),
                        viewer -> menus.openList(viewer, reported))));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }

    /** Each action's label has its explanation under {@code <key>-lore}, shown when hovering the button. */
    private static ActionButton button(Messages messages, String key, TagResolver placeholders, Consumer<Player> onClick) {
        return ActionButton.builder(messages.get(key, placeholders))
                .tooltip(messages.get(key + "-lore", placeholders))
                .action(Dialogs.action(onClick))
                .build();
    }

    /** A report somebody else holds says who, so a second moderator sees it before clicking. */
    private static String claim(Report report, UUID staffId, String staff) {
        if (report.isClaimedBy(staffId, staff)) {
            return "menu.release";
        }
        return report.claimedBy().isPresent() ? "menu.claimed" : "menu.claim";
    }
}
