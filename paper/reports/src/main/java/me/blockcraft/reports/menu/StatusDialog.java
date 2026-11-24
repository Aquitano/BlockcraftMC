package me.blockcraft.reports.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.Report;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** One text field for the report's new status; saving writes it, the exit button returns to the report. */
final class StatusDialog {

    private static final String STATUS = "status";
    private static final int MAX_LENGTH = 100;

    private final Dialog dialog;

    StatusDialog(ReportMenus menus, Messages messages, Report report, Optional<String> reported) {
        var placeholders = ReportMenus.placeholders(messages, report);
        dialog = Dialogs.create(messages.get("status.title", placeholders), List.of(),
                List.of(DialogInput.text(STATUS, messages.get("status.label"))
                        .maxLength(MAX_LENGTH)
                        .initial(report.status())
                        .build()),
                Dialogs.actions(List.of(Dialogs.button(messages.get("status.save"),
                                (viewer, view) -> menus.setStatus(viewer, report, reported, Dialogs.text(view, STATUS, MAX_LENGTH)))),
                        Dialogs.button(BlockcraftApi.get().messages().get("menu.back"), viewer -> menus.openReport(viewer, report, reported))));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
