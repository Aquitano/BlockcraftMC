package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.Plugin;

public final class ConnectCommand {

    private ConnectCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Messages messages) {
        return Commands.literal("connect")
                .requires(CorePermission.player("connect"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("connect.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("server", StringArgumentType.word())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            String server = StringArgumentType.getString(context, "server");
                            player.sendMessage(messages.get("connect.done", Placeholder.unparsed("server", server)));
                            ProxyMessaging.connect(plugin, player, server);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
