package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

public final class FlyCommand {

    private FlyCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("fly")
                .requires(CorePermission.player("fly"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (player.getAllowFlight()) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                        player.sendMessage(messages.get("fly.disabled"));
                    } else {
                        player.setAllowFlight(true);
                        player.setFlying(true);
                        player.sendMessage(messages.get("fly.enabled"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
