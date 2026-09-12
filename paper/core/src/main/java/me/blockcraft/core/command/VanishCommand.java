package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.UUID;

public final class VanishCommand {

    private VanishCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Messages messages) {
        var vanished = new HashSet<UUID>();
        return Commands.literal("v")
                .requires(CorePermission.player("vanish"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (vanished.remove(player.getUniqueId())) {
                        player.sendMessage(messages.get("vanish.disabled"));
                        Bukkit.getOnlinePlayers().forEach(other -> other.showPlayer(plugin, player));
                    } else {
                        vanished.add(player.getUniqueId());
                        player.sendMessage(messages.get("vanish.enabled"));
                        Bukkit.getOnlinePlayers().forEach(other -> other.hidePlayer(plugin, player));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
