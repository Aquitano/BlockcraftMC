package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.chat.ChatMute;
import org.bukkit.Bukkit;

public final class GlobalMuteCommand {

    private GlobalMuteCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(ChatMute chatMute, Messages messages) {
        return Commands.literal("globalmute")
                .requires(source -> source.getSender().hasPermission(LobbyPermissions.GLOBAL_MUTE))
                .executes(context -> {
                    Bukkit.broadcast(messages.get(chatMute.toggle() ? "chat.mute-on" : "chat.mute-off"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
