package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ClearChatCommand {

    private static final int BLANK_LINES = 121;

    private ClearChatCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("cc")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(LobbyPermissions.CLEAR_CHAT))
                .executes(context -> {
                    var sender = context.getSource().getPlayerOrThrow();
                    var notice = messages.get("chat.cleared", Placeholder.unparsed("player", sender.getName()));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.hasPermission(LobbyPermissions.CLEAR_CHAT)) {
                            for (int i = 0; i < BLANK_LINES; i++) {
                                player.sendMessage(Component.empty());
                            }
                        }
                        player.sendMessage(notice);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
