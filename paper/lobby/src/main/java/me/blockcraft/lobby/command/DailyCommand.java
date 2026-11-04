package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.lobby.daily.DailyCourier;
import org.bukkit.entity.Player;

public final class DailyCommand {

    private DailyCommand() { }

    public static LiteralCommandNode<CommandSourceStack> create(DailyCourier courier) {
        return Commands.literal("daily")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    courier.open(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }).build();
    }
}
