package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's side of /report: the proxy forwards {@code /report <player>} here, the dialog collects a reason from
 * the configured list plus free text, and the finished report goes back to the proxy's command, which stores it,
 * applies the cooldown and tells the staff.
 */
public final class ReportCommand {

    private static final String REASON = "reason";
    private static final String DETAILS = "details";
    private static final int MAX_DETAILS_LENGTH = 150;

    private ReportCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, List<String> reasons, Messages messages) {
        return Commands.literal("report")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("report.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            String target = StringArgumentType.getString(context, "player");
                            if (target.equalsIgnoreCase(BlockcraftApi.get().realName(player))) {
                                player.sendMessage(messages.get("report.self"));
                                return Command.SINGLE_SUCCESS;
                            }
                            Dialogs.show(player, dialog(plugin, target, reasons, messages));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static Dialog dialog(Plugin plugin, String target, List<String> reasons, Messages messages) {
        var placeholders = Placeholder.unparsed("player", target);
        var choices = reasons.stream().filter(reason -> !reason.isBlank()).distinct().toList();
        var options = choices.stream()
                .map(reason -> OptionEntry.create(reason, Component.text(reason), reason.equals(choices.getFirst())))
                .toList();
        var inputs = new ArrayList<DialogInput>();
        if (!options.isEmpty()) {
            inputs.add(DialogInput.singleOption(REASON, messages.get("report.reason"), options).build());
        }
        inputs.add(DialogInput.text(DETAILS, messages.get("report.details")).maxLength(MAX_DETAILS_LENGTH).build());
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("report.title", placeholders))
                        .body(List.of(DialogBody.plainMessage(messages.get("report.body", placeholders))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        Dialogs.button(messages.get("report.send"), (player, view) -> {
                            String reason = reason(view, choices);
                            if (reason.isBlank()) {
                                player.sendMessage(messages.get("report.reason-missing"));
                                Dialogs.show(player, dialog(plugin, target, reasons, messages));
                                return;
                            }
                            ProxyMessaging.runOnProxy(plugin, player, "report " + target + " " + reason);
                        }),
                        Dialogs.button(messages.get("report.cancel")))));
    }

    private static String reason(DialogResponseView view, List<String> choices) {
        String raw = view.getText(DETAILS);
        String details = raw == null ? "" : raw.strip();
        if (details.length() > MAX_DETAILS_LENGTH || details.chars().anyMatch(Character::isISOControl)) {
            return "";
        }
        String selected = view.getText(REASON);
        if (choices.isEmpty()) {
            return details;
        }
        if (selected == null || !choices.contains(selected)) {
            return "";
        }
        return details.isEmpty() ? selected : selected + " - " + details;
    }
}
