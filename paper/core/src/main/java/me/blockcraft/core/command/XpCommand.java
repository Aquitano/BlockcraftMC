package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class XpCommand {

    private XpCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("xp")
                .requires(CorePermission.player("xp"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("xp.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(messages.get("xp.usage"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    var sender = context.getSource().getSender();
                                    var target = Players.resolve(context, "player");
                                    if (!TargetGuard.mayTarget(sender, target, messages)) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    int level = IntegerArgumentType.getInteger(context, "level");
                                    sender.sendMessage(messages.get("xp.done",
                                            Placeholder.unparsed("player", target.getName()),
                                            Formatter.number("level", level)));
                                    target.setLevel(level);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
