package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

public final class RainCommand {

    private RainCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("rain")
                .requires(CorePermission.player("rain"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.getWorld().setStorm(true);
                    player.sendMessage(messages.get("rain.done"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
