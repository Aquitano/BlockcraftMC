package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Vanilla's {@code /xp add|set <targets> <amount> [points|levels]} and {@code /xp query <target> <points|levels>},
 * plus the older {@code /xp <player> <level>}, which sets levels.
 */
public final class XpCommand {

    private enum Operation { ADD, SET }

    private enum Unit { POINTS, LEVELS }

    private XpCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("xp")
                .requires(CorePermission.of("xp"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("xp.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("add").then(Commands.argument("targets", ArgumentTypes.players())
                        .then(amount(IntegerArgumentType.integer(), Operation.ADD, messages))))
                .then(Commands.literal("set").then(Commands.argument("targets", ArgumentTypes.players())
                        .then(amount(IntegerArgumentType.integer(0), Operation.SET, messages))))
                .then(Commands.literal("query").then(Commands.argument("target", ArgumentTypes.player())
                        .then(Commands.literal("points").executes(context -> query(context, Unit.POINTS, messages)))
                        .then(Commands.literal("levels").executes(context -> query(context, Unit.LEVELS, messages)))))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> change(context, Operation.SET, Unit.LEVELS, messages))))
                .build();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> amount(IntegerArgumentType type, Operation operation,
                                                                                Messages messages) {
        return Commands.argument("amount", type)
                .executes(context -> change(context, operation, Unit.POINTS, messages))
                .then(Commands.literal("points").executes(context -> change(context, operation, Unit.POINTS, messages)))
                .then(Commands.literal("levels").executes(context -> change(context, operation, Unit.LEVELS, messages)));
    }

    private static int change(CommandContext<CommandSourceStack> context, Operation operation, Unit unit, Messages messages)
            throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        for (var target : TargetGuard.permitted(sender, Players.resolveAll(context, "targets"), messages)) {
            change(sender, target, operation, unit, amount, messages);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void change(CommandSender sender, Player target, Operation operation, Unit unit, int amount, Messages messages) {
        var player = Placeholder.unparsed("player", target.getName());
        var number = Formatter.number("amount", amount);
        switch (operation) {
            case ADD -> {
                if (unit == Unit.LEVELS) {
                    target.giveExpLevels(amount);
                } else {
                    target.giveExp(amount);
                }
                sender.sendMessage(messages.get(unit == Unit.LEVELS ? "xp.added-levels" : "xp.added-points", player, number));
            }
            case SET -> {
                if (unit == Unit.LEVELS) {
                    target.setLevel(amount);
                } else if (amount > target.getExpToLevel()) {
                    sender.sendMessage(messages.get("xp.points-too-high", player, Formatter.number("max", target.getExpToLevel())));
                    return;
                } else {
                    target.setExp(amount / (float) target.getExpToLevel());
                }
                sender.sendMessage(messages.get(unit == Unit.LEVELS ? "xp.set-levels" : "xp.set-points", player, number));
            }
        }
    }

    private static int query(CommandContext<CommandSourceStack> context, Unit unit, Messages messages) throws CommandSyntaxException {
        var target = Players.resolve(context, "target");
        var player = Placeholder.unparsed("player", target.getName());
        context.getSource().getSender().sendMessage(unit == Unit.LEVELS
                ? messages.get("xp.query-levels", player, Formatter.number("amount", target.getLevel()))
                : messages.get("xp.query-points", player, Formatter.number("amount", Math.round(target.getExp() * target.getExpToLevel()))));
        return Command.SINGLE_SUCCESS;
    }
}
