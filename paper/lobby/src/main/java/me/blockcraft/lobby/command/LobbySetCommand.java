package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.config.ActivitiesConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class LobbySetCommand {

    private LobbySetCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Locations<String> locations, Messages messages,
                                                               ActivitiesConfig config, Runnable refresh) {
        var root = Commands.literal("lobbyset").requires(source -> source.getSender() instanceof Player player
                && player.hasPermission(LobbyPermissions.SET_SPAWN));
        var keys = new ArrayList<>(List.of("pond", "parkour-start", "parkour-finish", "parkour-top"));
        for (int i = 1; i <= config.checkpoints(); i++) {
            keys.add("checkpoint-" + i);
        }
        for (int i = 1; i <= config.easterSpots(); i++) {
            keys.add("egg-" + i);
        }
        for (var key : keys) {
            root.then(Commands.literal(key).executes(context -> {
                var player = context.getSource().getPlayerOrThrow();
                locations.set(key, player.getLocation());
                player.sendMessage(messages.get("activities.location-set", Placeholder.unparsed("location", key)));
                refresh.run();
                return Command.SINGLE_SUCCESS;
            }));
        }
        return root.build();
    }
}
