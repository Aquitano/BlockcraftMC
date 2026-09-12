package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

public final class FlySpeedCommand {

    private static final float DEFAULT_SPEED = 0.1f;
    private static final float FAST_SPEED = 0.5f;

    private FlySpeedCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("flyspeed")
                .requires(CorePermission.player("fly"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (player.getFlySpeed() > DEFAULT_SPEED) {
                        player.setFlySpeed(DEFAULT_SPEED);
                        player.sendMessage(messages.get("flyspeed.disabled"));
                    } else {
                        player.setFlySpeed(FAST_SPEED);
                        player.sendMessage(messages.get("flyspeed.enabled"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
