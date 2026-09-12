package me.blockcraft.system.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;

import java.util.function.Predicate;

/** Shared Brigadier glue: permission predicates, player-only executors, online-name suggestions. */
public final class CommandSupport {

    @FunctionalInterface
    public interface PlayerAction {
        void run(Player player, CommandContext<CommandSource> context);
    }

    @FunctionalInterface
    public interface SourceAction {
        void run(CommandSource source, CommandContext<CommandSource> context);
    }

    private final ProxyServer proxy;
    private final Messages messages;

    public CommandSupport(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    public static Predicate<CommandSource> permission(String node) {
        return source -> source.hasPermission(node);
    }

    public Command<CommandSource> usage(String key) {
        return context -> {
            context.getSource().sendMessage(messages.get(key));
            return Command.SINGLE_SUCCESS;
        };
    }

    public Command<CommandSource> run(SourceAction action) {
        return context -> {
            action.run(context.getSource(), context);
            return Command.SINGLE_SUCCESS;
        };
    }

    public Command<CommandSource> asPlayer(PlayerAction action) {
        return context -> {
            if (context.getSource() instanceof Player player) {
                action.run(player, context);
            } else {
                context.getSource().sendMessage(messages.get("player-only"));
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    public RequiredArgumentBuilder<CommandSource, String> playerArgument(String name) {
        return BrigadierCommand.requiredArgumentBuilder(name, StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemaining().toLowerCase();
                    proxy.getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(username -> username.toLowerCase().startsWith(typed))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    public static String senderName(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : "Console";
    }

    public static String currentServer(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
    }
}
