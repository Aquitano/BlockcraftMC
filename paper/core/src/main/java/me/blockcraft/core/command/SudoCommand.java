package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Set;

public final class SudoCommand {

    private SudoCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages, Set<String> protectedNames) {
        return Commands.literal("sudo")
                .requires(CorePermission.player("sudo"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("sudo.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(messages.get("sudo.usage"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    var sender = context.getSource().getSender();
                                    var target = Players.resolve(context, "player");
                                    if (!TargetGuard.notSelf(sender, target, messages) || !TargetGuard.mayTarget(sender, target, messages)) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (target.isOp() || protectedNames.contains(target.getName().toLowerCase())) {
                                        sender.sendMessage(messages.get("sudo.protected"));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String message = StringArgumentType.getString(context, "message");
                                    sender.sendMessage(messages.get("sudo.done",
                                            Placeholder.unparsed("player", target.getName()),
                                            Placeholder.unparsed("message", message)));
                                    target.chat(message);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
