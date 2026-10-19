package me.blockcraft.reports.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
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
        dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("status.title", placeholders))
                        .inputs(List.of(DialogInput.text(STATUS, messages.get("status.label"))
                                .initial(report.status())
                                .maxLength(MAX_LENGTH)
                                .build()))
                        .build())
                .type(DialogType.multiAction(List.of(Dialogs.button(messages.get("status.save"),
                                (viewer, view) -> menus.setStatus(viewer, report, reported, view.getText(STATUS)))))
                        .exitAction(Dialogs.button(BlockcraftApi.get().messages().get("menu.back"), viewer -> menus.openReport(viewer, report, reported)))
                        .build()));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }
}
