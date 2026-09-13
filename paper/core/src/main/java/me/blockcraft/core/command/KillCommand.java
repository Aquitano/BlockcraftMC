package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class KillCommand {

    private KillCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("kill")
                .requires(CorePermission.player("kill"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("kill.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            var target = Players.resolve(context, "player");
                            if (!TargetGuard.mayTarget(sender, target, messages)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            sender.sendMessage(messages.get("kill.done", Placeholder.unparsed("player", target.getName())));
                            target.setHealth(0.0);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
