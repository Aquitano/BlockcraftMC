package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.TopList;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class TopCommand {

    private TopCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(TopList topList, Messages messages) {
        return Commands.literal("top")
                .requires(source -> source.getSender() instanceof Player)
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Arrays.stream(Stat.values()).map(Stat::key).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrThrow();
                            Stat.parse(StringArgumentType.getString(context, "category")).ifPresentOrElse(
                                    column -> topList.send(player, column),
                                    () -> player.sendMessage(messages.get("top.unknown-category")));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
