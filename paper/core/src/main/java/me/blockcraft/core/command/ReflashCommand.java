package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ReflashCommand {

    private ReflashCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Messages messages) {
        return Commands.literal("reflash")
                .requires(CorePermission.player("reflash"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    for (var viewer : Bukkit.getOnlinePlayers()) {
                        viewer.hidePlayer(plugin, player);
                        viewer.showPlayer(plugin, player);
                    }
                    player.sendMessage(messages.get("reflash.done"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
