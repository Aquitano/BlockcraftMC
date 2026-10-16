package me.blockcraft.system.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.rank.ProxyRanks;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    private final PlayerVisibility visibility;
    private final Messages messages;

    public CommandSupport(PlayerVisibility visibility, Messages messages) {
        this.visibility = visibility;
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

    /** Staff may not punish themselves, nor anyone the rank ladder does not put below them; the console always may. */
    public boolean mayModerate(CommandSource source, UUID target) {
        if (!(source instanceof Player actor)) {
            return true;
        }
        if (actor.getUniqueId().equals(target)) {
            source.sendMessage(messages.get("moderation.self"));
            return false;
        }
        if (actor.hasPermission(SystemPermissions.BYPASS_RANK) || ProxyRanks.outranks(actor, target)) {
            return true;
        }
        source.sendMessage(messages.get("moderation.protected"));
        return false;
    }

    /** Lifting or replacing what someone handed out also needs them not to outrank the source; the console always may. */
    public boolean mayOverrule(CommandSource source, PlayerReference issuer) {
        if (!(source instanceof Player actor) || actor.hasPermission(SystemPermissions.BYPASS_RANK)
                || issuer.uuid().filter(id -> ProxyRanks.ranksAbove(id, actor)).isEmpty()) {
            return true;
        }
        source.sendMessage(messages.get("moderation.issuer-outranks"));
        return false;
    }

    public RequiredArgumentBuilder<CommandSource, String> playerArgument(String name) {
        return BrigadierCommand.requiredArgumentBuilder(name, StringArgumentType.word())
                .suggests((context, builder) -> suggestPlayers(context.getSource(), builder));
    }

    /** Completes the names the source would see in the tab list. */
    public CompletableFuture<Suggestions> suggestPlayers(CommandSource source, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        visibility.namesFor(source).filter(shown -> shown.toLowerCase().startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /** Who acts: the player's account, or the console by name alone. */
    public static PlayerReference sender(CommandSource source) {
        return source instanceof Player player ? PlayerReference.known(player.getUniqueId(), player.getUsername()) : PlayerReference.named("Console");
    }

    public static String currentServer(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
    }
}
