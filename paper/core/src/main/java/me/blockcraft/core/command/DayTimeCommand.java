package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;

/** {@code /day} and {@code /night} for the sender's world; vanilla's {@code /time} stays reachable under its own name. */
public final class DayTimeCommand {

    private static final long DAY = 6000L;
    private static final long NIGHT = 18000L;

    private DayTimeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> day(Messages messages) {
        return create("day", DAY, messages.get("time.day"));
    }

    public static LiteralCommandNode<CommandSourceStack> night(Messages messages) {
        return create("night", NIGHT, messages.get("time.night"));
    }

    private static LiteralCommandNode<CommandSourceStack> create(String name, long ticks, Component done) {
        return Commands.literal(name)
                .requires(CorePermission.of("time"))
                .executes(context -> {
                    Worlds.setTime(context.getSource().getLocation().getWorld(), ticks);
                    context.getSource().getSender().sendMessage(done);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
