package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

public final class ServerInfoCommand {

    private static final long MEBIBYTE = 1024L * 1024L;

    private ServerInfoCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("serverinfo")
                .requires(CorePermission.player("serverinfo"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    var runtime = Runtime.getRuntime();
                    long max = runtime.maxMemory() / MEBIBYTE;
                    long used = (runtime.totalMemory() - runtime.freeMemory()) / MEBIBYTE;
                    messages.getLines("serverinfo.lines", Formatter.number("max", max), Formatter.number("used", used))
                            .forEach(player::sendMessage);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
