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
import me.blockcraft.lobby.npc.Holograms;
import me.blockcraft.lobby.npc.ShopNpc;
import org.bukkit.entity.Player;

import java.util.List;

public final class SetHoloCommand {

    private static final List<String> HOLOGRAMS = List.of("syndicate", "skypvp", "farm");

    private SetHoloCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages, Locations<LobbyLocation> locations, ShopNpc shopNpc) {
        var root = Commands.literal("setholo")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(LobbyPermissions.SET_HOLO))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("setholo.usage"));
                    return Command.SINGLE_SUCCESS;
                });
        HOLOGRAMS.forEach(name -> root.then(hologram(name, messages)));
        root.then(Commands.literal("shop").executes(context -> {
            var player = context.getSource().getPlayerOrThrow();
            locations.set(LobbyLocation.SHOP_NPC, player.getLocation());
            shopNpc.respawn();
            player.sendMessage(messages.get("setholo.shop-set"));
            return Command.SINGLE_SUCCESS;
        }));
        return root.build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hologram(String name, Messages messages) {
        return Commands.literal(name).executes(context -> {
            var player = context.getSource().getPlayerOrThrow();
            Holograms.spawn(player.getLocation(), messages.getLines("holo." + name));
            player.sendMessage(messages.get("setholo.set"));
            return Command.SINGLE_SUCCESS;
        });
    }
}
