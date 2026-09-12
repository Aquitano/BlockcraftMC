package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import org.bukkit.entity.Player;

public final class BuildCommand {

    private BuildCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game, Messages messages) {
        return Commands.literal("build")
                .requires(source -> source.getSender() instanceof Player
                        && source.getSender().hasPermission("blockcraft.quicksg.build"))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get(game.toggleBuilder(player) ? "build.enabled" : "build.disabled"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
