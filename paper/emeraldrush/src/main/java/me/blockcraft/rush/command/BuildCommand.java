package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.game.Game;
import org.bukkit.entity.Player;

public final class BuildCommand {

    private BuildCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game, Messages messages) {
        return Commands.literal("build")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(Permissions.BUILD))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    boolean enabled = game.toggleBuilder(player);
                    player.sendMessage(messages.get(enabled ? "command.build-enabled" : "command.build-disabled"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
