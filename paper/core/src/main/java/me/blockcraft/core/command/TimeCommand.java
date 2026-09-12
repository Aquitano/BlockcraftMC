package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.api.world.Worlds;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

import java.util.OptionalLong;

public final class TimeCommand {

    private static final long DAY = 6000L;
    private static final long NIGHT = 18000L;

    private TimeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("time")
                .requires(CorePermission.player("time"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("time.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("time", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("day").suggest("night");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            var world = player.getWorld();
                            String input = StringArgumentType.getString(context, "time");
                            switch (input.toLowerCase()) {
                                case "day", "tag" -> {
                                    Worlds.setTime(world, DAY);
                                    player.sendMessage(messages.get("time.day"));
                                }
                                case "night", "nacht" -> {
                                    Worlds.setTime(world, NIGHT);
                                    player.sendMessage(messages.get("time.night"));
                                }
                                default -> {
                                    var ticks = parse(input);
                                    if (ticks.isEmpty()) {
                                        player.sendMessage(messages.get("time.invalid"));
                                    } else {
                                        Worlds.setTime(world, ticks.getAsLong());
                                        player.sendMessage(messages.get("time.set", Formatter.number("time", ticks.getAsLong())));
                                    }
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static OptionalLong parse(String input) {
        try {
            return OptionalLong.of(Long.parseLong(input));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
