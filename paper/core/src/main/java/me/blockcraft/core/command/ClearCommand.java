package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ClearCommand {

    private ClearCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("clear")
                .requires(CorePermission.player("clear"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("clear.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            var target = Players.resolve(context, "player");
                            context.getSource().getSender().sendMessage(messages.get("clear.done", Placeholder.unparsed("player", target.getName())));
                            target.getInventory().clear();
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
