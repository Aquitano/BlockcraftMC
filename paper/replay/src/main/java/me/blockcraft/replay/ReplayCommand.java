package me.blockcraft.replay;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

import java.util.UUID;

final class ReplayCommand {

    private ReplayCommand() { }

    static LiteralCommandNode<CommandSourceStack> create(ReplayService service, Messages messages) {
        return Commands.literal("replay")
                .requires(source -> source.getSender().hasPermission(ReplayPlugin.USE_PERMISSION))
                .executes(context -> {
                    service.open(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("play").then(Commands.argument("id", StringArgumentType.word()).executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    UUID replay;
                    try {
                        replay = UUID.fromString(StringArgumentType.getString(context, "id"));
                    } catch (IllegalArgumentException error) {
                        player.sendMessage(messages.get("playback.invalid-id"));
                        return 0;
                    }
                    service.play(player, replay);
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("stop").executes(context -> {
                    service.stop(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("pause").executes(context -> {
                    service.toggle(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("speed").then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.5, 2))
                        .suggests((context, builder) -> builder.suggest("0.5").suggest("1").suggest("2").buildFuture()).executes(context -> {
                    service.speed(context.getSource().getPlayerOrThrow(), DoubleArgumentType.getDouble(context, "speed"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("seek").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(context -> {
                    service.seek(context.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(context, "seconds"));
                    return Command.SINGLE_SUCCESS;
                })))
                .build();
    }
}
