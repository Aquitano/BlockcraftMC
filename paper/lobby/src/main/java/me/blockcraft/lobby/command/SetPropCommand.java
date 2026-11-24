package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.activity.Props;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** /setprop: staff stand where a set piece belongs and name it; it stands there from now on, after restarts too. */
public final class SetPropCommand {

    private SetPropCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages, Props props) {
        var root = Commands.literal("setprop")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(LobbyPermissions.SET_HOLO))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("setprop.usage"));
                    return Command.SINGLE_SUCCESS;
                });
        props.names().stream().sorted().forEach(name -> root.then(Commands.literal(name).executes(context -> {
            var player = context.getSource().getPlayerOrThrow();
            props.place(name, player.getLocation());
            player.sendMessage(messages.get("setprop.set", Placeholder.unparsed("prop", name)));
            return Command.SINGLE_SUCCESS;
        })));
        return root.build();
    }
}
