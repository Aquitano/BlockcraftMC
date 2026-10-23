package me.blockcraft.reports.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentCatalogRepository.Entry;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.report.Report;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The /strafe catalogue as buttons, each requesting the numbered punishment from the proxy, plus a form for a
 * punishment the catalogue lacks: type, duration and reason go out as a timed punishment request.
 */
final class PunishDialog {

    record Custom(PunishmentType type, int days, int hours, String reason) {

        int totalHours() {
            return days * HOURS_PER_DAY + hours;
        }
    }

    private static final String TYPE = "type";
    private static final String DAYS = "days";
    private static final String HOURS = "hours";
    private static final String REASON = "reason";
    private static final int HOURS_PER_DAY = 24;
    private static final int MAX_DAYS = 90;
    private static final int MAX_REASON_LENGTH = 200;

    private final Dialog dialog;

    PunishDialog(ReportMenus menus, Messages messages, Report report, Optional<String> reported, List<Entry> catalog,
                 Optional<Custom> draft) {
        var placeholders = ReportMenus.placeholders(messages, report);
        var buttons = new ArrayList<ActionButton>();
        for (var entry : catalog) {
            var entryPlaceholders = TagResolver.resolver(
                    Formatter.number("number", entry.number()),
                    Placeholder.unparsed("reason", entry.reason()),
                    Placeholder.component("type", messages.get("menu.punish-type." + entry.type().name().toLowerCase())),
                    placeholders);
            buttons.add(ActionButton.builder(messages.get("menu.punish-entry", entryPlaceholders))
                    .tooltip(Component.join(JoinConfiguration.newlines(), messages.getLines("menu.punish-entry-lore", entryPlaceholders)))
                    .action(Dialogs.action(viewer -> menus.punish(viewer, report, entry)))
                    .build());
        }
        buttons.add(Dialogs.button(messages.get("punish.custom"), (viewer, view) -> menus.punish(viewer, report, reported, custom(view))));

        dialog = Dialogs.create(messages.get("menu.title-punish", placeholders),
                List.of(DialogBody.plainMessage(messages.get("punish.body", placeholders))),
                List.of(
                                DialogInput.singleOption(TYPE, messages.get("punish.type"),
                                        typeOptions(messages, draft.map(Custom::type).orElse(PunishmentType.BAN))).build(),
                                DialogInput.numberRange(DAYS, messages.get("punish.days"), 0, MAX_DAYS).step(1f)
                                        .initial(draft.map(custom -> (float) custom.days()).orElse(1f)).build(),
                                DialogInput.numberRange(HOURS, messages.get("punish.hours"), 0, HOURS_PER_DAY - 1).step(1f)
                                        .initial(draft.map(custom -> (float) custom.hours()).orElse(0f)).build(),
                                DialogInput.text(REASON, messages.get("punish.reason")).maxLength(MAX_REASON_LENGTH)
                                        .initial(draft.map(Custom::reason).orElse("")).build()),
                Dialogs.actions(buttons, Dialogs.button(BlockcraftApi.get().messages().get("menu.back"),
                        viewer -> menus.openReport(viewer, report, reported))));
    }

    void open(Player player) {
        Dialogs.show(player, dialog);
    }

    private static List<OptionEntry> typeOptions(Messages messages, PunishmentType selected) {
        return Arrays.stream(PunishmentType.values())
                .map(type -> OptionEntry.create(type.name(), messages.get("menu.punish-type." + type.name().toLowerCase()),
                        type == selected))
                .toList();
    }

    private static Optional<Custom> custom(DialogResponseView view) {
        String type = view.getText(TYPE);
        Float days = view.getFloat(DAYS);
        Float hours = view.getFloat(HOURS);
        var reason = Dialogs.text(view, REASON, MAX_REASON_LENGTH);
        if (type == null || Arrays.stream(PunishmentType.values()).noneMatch(value -> value.name().equals(type))
                || days == null || !Float.isFinite(days) || days < 0 || days > MAX_DAYS || days != Math.rint(days)
                || hours == null || !Float.isFinite(hours) || hours < 0 || hours >= HOURS_PER_DAY || hours != Math.rint(hours)
                || reason.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Custom(PunishmentType.valueOf(type), Math.round(days), Math.round(hours), reason.get()));
    }
}
