package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** {@code /top <stat>}, the top ten of the game's {@link TopList}. */
public final class TopCommand {

    private TopCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(TopList topList) {
        var top = Commands.literal("top").requires(source -> source.getSender() instanceof Player);
        for (var stat : Stat.values()) {
            top.then(Commands.literal(stat.key()).executes(context -> {
                topList.send(context.getSource().getPlayerOrThrow(), stat);
                return Command.SINGLE_SUCCESS;
            }));
        }
        return top.build();
    }
}
