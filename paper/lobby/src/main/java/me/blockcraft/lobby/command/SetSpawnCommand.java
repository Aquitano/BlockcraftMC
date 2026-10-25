package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.config.LobbyLocation;
import org.bukkit.entity.Player;

public final class SetSpawnCommand {

    private SetSpawnCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Locations<LobbyLocation> locations, Messages messages) {
        var root = Commands.literal("setspawn")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(LobbyPermissions.SET_SPAWN));
        root.then(target("spawn", LobbyLocation.SPAWN, locations, messages));
        root.then(target("duell", LobbyLocation.DUELL, locations, messages));
        root.then(target("slimebrawl", LobbyLocation.QSG, locations, messages));
        root.then(target("jump", LobbyLocation.JUMP, locations, messages));
        root.then(target("rush", LobbyLocation.RUSH, locations, messages));
        root.then(target("bedwars", LobbyLocation.BEDWARS, locations, messages));
        root.then(target("zombies", LobbyLocation.ZOMBIES, locations, messages));
        root.then(target("shop", LobbyLocation.SHOP, locations, messages));
        root.then(target("premium", LobbyLocation.PREMIUM, locations, messages));
        return root.build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> target(String literal, LobbyLocation location,
                                                                     Locations<LobbyLocation> locations, Messages messages) {
        return Commands.literal(literal).executes(context -> {
            var player = context.getSource().getPlayerOrThrow();
            locations.set(location, player.getLocation());
            player.sendMessage(messages.get("setspawn.set"));
            return Command.SINGLE_SUCCESS;
        });
    }
}
