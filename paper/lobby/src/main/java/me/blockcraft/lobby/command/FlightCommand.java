package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.activity.Flights;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** /ueberflug: staff send one of the lobby's flights over themselves now, to show it off or to check a new model. */
public final class FlightCommand {

    private FlightCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Flights flights, Messages messages) {
        var root = Commands.literal("ueberflug")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(LobbyPermissions.SET_SPAWN))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("flights.usage",
                            Placeholder.unparsed("models", String.join(", ", flights.models()))));
                    return Command.SINGLE_SUCCESS;
                });
        flights.models().forEach(model -> root.then(Commands.literal(model).executes(context -> {
            var player = context.getSource().getPlayerOrThrow();
            if (!flights.launch(model, player.getLocation())) {
                player.sendMessage(messages.get("flights.busy"));
                return 0;
            }
            return Command.SINGLE_SUCCESS;
        })));
        return root.build();
    }
}
