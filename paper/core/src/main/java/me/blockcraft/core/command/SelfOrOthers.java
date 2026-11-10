package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A command that acts on whoever runs it, or with {@code blockcraft.core.<command>.others} on each player its
 * {@code players} argument names that {@link TargetGuard} lets through. A console without a target gets
 * {@code <command>.usage}.
 */
final class SelfOrOthers {

    private static final Command<CommandSourceStack> NOTHING = context -> Command.SINGLE_SUCCESS;

    /** Acts on one target and tells them; what the sender hears when that target was someone else is the outcome. */
    @FunctionalInterface
    interface Action {
        Outcome apply(CommandSender sender, Player target);
    }

    /** Only for a target other than the sender: the message gets the target as {@code <player>} besides its own placeholders. */
    record Outcome(String otherMessage, TagResolver placeholders, BiConsumer<CommandSender, Player> audit) {

        static Outcome told(String otherMessage, TagResolver... placeholders) {
            return new Outcome(otherMessage, TagResolver.resolver(placeholders), (sender, target) -> {
            });
        }

        Outcome audited(StaffAudit log, String action) {
            return new Outcome(otherMessage, placeholders, (sender, target) -> log.record(sender, action, target));
        }

        Outcome audited(StaffAudit log, String action, String detail) {
            return new Outcome(otherMessage, placeholders, (sender, target) -> log.record(sender, action, target, detail));
        }
    }

    private final String command;
    private final Messages messages;

    SelfOrOthers(String command, Messages messages) {
        this.command = command;
        this.messages = messages;
    }

    <T extends ArgumentBuilder<CommandSourceStack, T>> T executes(T node, Action action) {
        return node.executes(self(action)).then(players().executes(others(action)));
    }

    /** For an action read from the node's arguments; when they name none, the sender gets the usage and no target is resolved. */
    <T extends ArgumentBuilder<CommandSourceStack, T>> T executes(T node,
                                                                  Function<CommandContext<CommandSourceStack>, Optional<Action>> read) {
        return node
                .executes(context -> chosen(context, read).map(this::self).orElse(NOTHING).run(context))
                .then(players().executes(context -> chosen(context, read).map(this::others).orElse(NOTHING).run(context)));
    }

    Command<CommandSourceStack> self(Action action) {
        return context -> {
            Players.self(context.getSource(), messages.get(command + ".usage")).ifPresent(player -> act(player, player, action));
            return Command.SINGLE_SUCCESS;
        };
    }

    Command<CommandSourceStack> others(Action action) {
        return context -> {
            var sender = context.getSource().getSender();
            TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                    .forEach(target -> act(sender, target, action));
            return Command.SINGLE_SUCCESS;
        };
    }

    /** The target argument, for a command whose own arguments follow it. */
    RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> players() {
        return Commands.argument("players", ArgumentTypes.players()).requires(CorePermission.of(command + ".others"));
    }

    private Optional<Action> chosen(CommandContext<CommandSourceStack> context,
                                    Function<CommandContext<CommandSourceStack>, Optional<Action>> read) {
        var action = read.apply(context);
        if (action.isEmpty()) {
            context.getSource().getSender().sendMessage(messages.get(command + ".usage"));
        }
        return action;
    }

    private void act(CommandSender sender, Player target, Action action) {
        var outcome = action.apply(sender, target);
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get(outcome.otherMessage(), outcome.placeholders(),
                    Placeholder.unparsed("player", target.getName())));
            outcome.audit().accept(sender, target);
        }
    }
}
