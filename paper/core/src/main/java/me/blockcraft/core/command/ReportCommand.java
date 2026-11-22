package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.chat.ChatPrompt;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.NetworkRequest.SubmitReport;
import me.blockcraft.common.player.PlayerReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * The player's side of /report: the proxy forwards {@code /report <player> [reason]} here. Without a reason the player
 * picks one from buttons in chat; the finished report goes back to the proxy as a {@link SubmitReport}, which resolves
 * the name the player sees, stores the report, applies the cooldown and tells the staff.
 */
public final class ReportCommand {

    private ReportCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(List<String> configuredReasons, Messages messages) {
        var reasons = configuredReasons.stream().filter(reason -> !reason.isBlank()).distinct().toList();
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
                            if (reportable(player, target, messages)) {
                                ask(player, target, reasons, messages);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrThrow();
                                    String target = StringArgumentType.getString(context, "player");
                                    if (reportable(player, target, messages)) {
                                        submit(player, target, StringArgumentType.getString(context, "reason"), reasons, messages);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    /** A configured reason, matched in any case, with the typed rest as its details; free text when none are configured. */
    static Optional<String> reason(String typed, List<String> reasons) {
        String text = typed.strip();
        if (reasons.isEmpty()) {
            return Optional.of(text).filter(value -> !value.isEmpty());
        }
        var parts = text.split("\\s+", 2);
        String details = parts.length > 1 ? parts[1] : "";
        return reasons.stream()
                .filter(reason -> reason.equalsIgnoreCase(parts[0]))
                .findFirst()
                .map(reason -> details.isEmpty() ? reason : reason + " - " + details);
    }

    private static boolean reportable(Player player, String target, Messages messages) {
        if (!PlayerReference.isName(target)) {
            player.sendMessage(messages.get("report.invalid-player"));
            return false;
        }
        if (target.equalsIgnoreCase(BlockcraftApi.get().realName(player))) {
            player.sendMessage(messages.get("report.self"));
            return false;
        }
        return true;
    }

    private static void ask(Player player, String target, List<String> reasons, Messages messages) {
        var name = Placeholder.unparsed("player", target);
        if (!reasons.isEmpty()) {
            var prompt = new ChatPrompt(player);
            var buttons = reasons.stream().map(reason -> {
                var named = Placeholder.unparsed("reason", reason);
                return prompt.button(messages.get("report.reason-button", named), messages.get("report.reason-hover", name, named),
                        reporter -> send(reporter, target, reason));
            }).toList();
            player.sendMessage(messages.get("report.prompt", name,
                    Placeholder.component("reasons", Component.join(JoinConfiguration.spaces(), buttons))));
        }
        player.sendMessage(messages.get(reasons.isEmpty() ? "report.details-only" : "report.details-hint", name)
                .hoverEvent(HoverEvent.showText(messages.get("report.details-hover")))
                .clickEvent(ClickEvent.suggestCommand("/report " + target + " ")));
    }

    private static void submit(Player player, String target, String typed, List<String> reasons, Messages messages) {
        var reason = reason(typed, reasons);
        if (reason.isEmpty()) {
            player.sendMessage(messages.get("report.reason-missing"));
            ask(player, target, reasons, messages);
            return;
        }
        if (!NetworkRequest.isText(reason.get())) {
            player.sendMessage(messages.get("report.too-long", Formatter.number("max", NetworkRequest.MAX_TEXT_LENGTH)));
            return;
        }
        send(player, target, reason.get());
    }

    private static void send(Player player, String target, String reason) {
        BlockcraftApi.get().operations().submit(player, new SubmitReport(target, reason));
    }
}
