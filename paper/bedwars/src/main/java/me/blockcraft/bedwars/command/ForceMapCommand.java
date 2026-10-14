package me.blockcraft.bedwars.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.bedwars.Permissions;
import me.blockcraft.bedwars.game.Game;

/** Ends the map vote early with a map of the caller's choice, until its last seconds. */
public final class ForceMapCommand {

    private ForceMapCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game) {
        return Commands.literal("forcemap")
                .requires(source -> source.getSender().hasPermission(Permissions.FORCE_MAP))
                .then(Commands.argument("map", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            game.playableMaps().stream().filter(key -> key.startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            game.forceMap(context.getSource().getSender(), StringArgumentType.getString(context, "map"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
