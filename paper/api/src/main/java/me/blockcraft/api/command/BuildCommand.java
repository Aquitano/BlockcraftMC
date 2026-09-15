package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;

public final class BuildCommand {

    private BuildCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(BuildMode buildMode, Messages messages) {
        return Commands.literal("build")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("blockcraft.build"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get(buildMode.toggle(player) ? "build.enabled" : "build.disabled"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
