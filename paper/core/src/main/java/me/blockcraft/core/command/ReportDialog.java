package me.blockcraft.core.command;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest.SubmitReport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A reason from the configured list plus free text, or the text alone when no reasons are configured; a form without
 * a usable reason comes back with what was entered.
 */
final class ReportDialog {

    private static final String REASON = "reason";
    private static final String DETAILS = "details";
    private static final int MAX_DETAILS_LENGTH = 150;

    private final Dialog dialog;

    ReportDialog(String target, List<String> reasons, Messages messages) {
        this(target, reasons.stream().filter(reason -> !reason.isBlank()).distinct().toList(), messages, "", "");
    }

    private ReportDialog(String target, List<String> choices, Messages messages, String chosen, String details) {
        var placeholders = Placeholder.unparsed("player", target);
        String selected = choices.contains(chosen) ? chosen : choices.isEmpty() ? "" : choices.getFirst();
        var inputs = new ArrayList<DialogInput>();
        if (!choices.isEmpty()) {
            inputs.add(DialogInput.singleOption(REASON, messages.get("report.reason"), choices.stream()
                    .map(reason -> OptionEntry.create(reason, Component.text(reason), reason.equals(selected)))
                    .toList()).build());
        }
        inputs.add(DialogInput.text(DETAILS, messages.get("report.details"))
                .maxLength(MAX_DETAILS_LENGTH)
                .initial(details)
                .build());
        dialog = Dialogs.create(messages.get("report.title", placeholders),
                List.of(DialogBody.plainMessage(messages.get("report.body", placeholders))),
                inputs,
                DialogType.confirmation(
                        Dialogs.button(messages.get("report.send"), (player, view) -> {
                            String picked = Objects.requireNonNullElse(view.getText(REASON), "");
                            var typed = Dialogs.text(view, DETAILS, MAX_DETAILS_LENGTH);
                            var reason = typed.flatMap(text -> reason(picked, choices, text));
                            if (reason.isEmpty()) {
                                player.sendMessage(messages.get("report.reason-missing"));
                                new ReportDialog(target, choices, messages, picked, typed.orElse("")).open(player);
                                return;
                            }
                            BlockcraftApi.get().operations().submit(player, new SubmitReport(target, reason.get()));
                        }),
                        Dialogs.button(BlockcraftApi.get().messages().get("menu.cancel"))));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }

    private static Optional<String> reason(String selected, List<String> choices, String details) {
        if (choices.isEmpty()) {
            return Optional.of(details).filter(text -> !text.isEmpty());
        }
        if (!choices.contains(selected)) {
            return Optional.empty();
        }
        return Optional.of(details.isEmpty() ? selected : selected + " - " + details);
    }
}
