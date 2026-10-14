package me.blockcraft.bedwars.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.bedwars.Permissions;
import me.blockcraft.bedwars.game.Game;
import org.bukkit.entity.Player;

public final class StartCommand {

    private StartCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game) {
        return Commands.literal("start")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(Permissions.START))
                .executes(context -> {
                    game.forceStart(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
