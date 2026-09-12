package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class TpCommand {

    private TpCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("tp")
                .requires(CorePermission.player("tp"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("tp.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            var target = Players.resolve(context, "player");
                            player.sendMessage(messages.get("tp.self", Placeholder.unparsed("player", target.getName())));
                            player.teleport(target);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("to", ArgumentTypes.player())
                                .executes(context -> {
                                    var from = Players.resolve(context, "player");
                                    var to = Players.resolve(context, "to");
                                    context.getSource().getSender().sendMessage(messages.get("tp.other",
                                            Placeholder.unparsed("from", from.getName()),
                                            Placeholder.unparsed("to", to.getName())));
                                    from.teleport(to);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
