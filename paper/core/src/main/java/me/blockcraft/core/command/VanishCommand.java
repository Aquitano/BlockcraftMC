package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.listener.VanishListener;

public final class VanishCommand {

    private VanishCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(VanishListener vanish, Messages messages) {
        return Commands.literal("v")
                .requires(CorePermission.player("vanish"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get(vanish.toggle(player) ? "vanish.enabled" : "vanish.disabled"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
