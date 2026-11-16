package me.blockcraft.system.report;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;

import java.util.Locale;

/** {@code /report}: the notification toggle for staff; the report itself is the backend's, which knows the reasons. */
public final class ReportCommand {

    private final NotifyPreferences preferences;
    private final Messages messages;
    private final PlayerVisibility visibility;

    public ReportCommand(NotifyPreferences preferences, Messages messages, PlayerVisibility visibility) {
        this.preferences = preferences;
        this.messages = messages;
        this.visibility = visibility;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("report")
                .executes(asPlayer((player, context) -> player.sendMessage(messages.get("report.usage"))))
                .then(BrigadierCommand.literalArgumentBuilder("notify")
                        .requires(source -> source.hasPermission(SystemPermissions.REPORT_NOTIFY))
                        .executes(asStaff((player, context) -> player.sendMessage(
                                messages.get(preferences.enabled(player) ? "report.notify.status-on" : "report.notify.status-off"))))
                        .then(BrigadierCommand.literalArgumentBuilder("on").executes(asStaff((player, context) -> toggle(player, true))))
                        .then(BrigadierCommand.literalArgumentBuilder("off").executes(asStaff((player, context) -> toggle(player, false))))
                        .then(BrigadierCommand.requiredArgumentBuilder("state", StringArgumentType.word())
                                .executes(asStaff((player, context) -> player.sendMessage(messages.get("report.notify.usage"))))))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
                            visibility.namesFor(context.getSource())
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::forward)
                        .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(this::forward))));
    }

    private int forward(CommandContext<CommandSource> context) {
        if (context.getSource() instanceof Player) {
            return BrigadierCommand.FORWARD;
        }
        context.getSource().sendMessage(messages.get("player-only"));
        return Command.SINGLE_SUCCESS;
    }

    private void toggle(Player player, boolean enabled) {
        if (enabled == preferences.enabled(player)) {
            player.sendMessage(messages.get(enabled ? "report.notify.already-enabled" : "report.notify.already-disabled"));
            return;
        }
        try {
            preferences.set(player, enabled);
        } catch (DatabaseException e) {
            player.sendMessage(messages.get("report.notify.failed"));
            return;
        }
        player.sendMessage(messages.get(enabled ? "report.notify.enabled" : "report.notify.disabled"));
    }

    private Command<CommandSource> asPlayer(PlayerAction action) {
        return context -> {
            if (!(context.getSource() instanceof Player player)) {
                context.getSource().sendMessage(messages.get("player-only"));
                return Command.SINGLE_SUCCESS;
            }
            action.run(player, context);
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<CommandSource> asStaff(PlayerAction action) {
        return asPlayer((player, context) -> {
            if (player.hasPermission(SystemPermissions.REPORT_NOTIFY)) {
                action.run(player, context);
            } else {
                player.sendMessage(messages.get("no-permission"));
            }
        });
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player, CommandContext<CommandSource> context);
    }
}
