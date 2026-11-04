package me.blockcraft.ttt.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.game.TttGame;
import me.blockcraft.ttt.listener.ChatListener;
import org.bukkit.entity.Player;

/** /tc: a message to the other traitors, which goes through the chat like any other. */
public final class TraitorChatCommand {

    private TraitorChatCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(TttGame game, Messages messages) {
        return Commands.literal("tc")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("chat.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            var player = (Player) context.getSource().getSender();
                            if (!game.traitorChat().contains(player.getUniqueId())) {
                                player.sendMessage(messages.get("chat.not-traitor"));
                                return Command.SINGLE_SUCCESS;
                            }
                            player.chat(ChatListener.TRAITOR_CHAT + StringArgumentType.getString(context, "message"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
