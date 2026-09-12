package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class RushSetCommand {

    private RushSetCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Spawns spawns, Messages messages) {
        return Commands.literal("rushset")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(Permissions.SETUP))
                .executes(context -> help(context.getSource().getPlayerOrThrow(), messages))
                .then(Commands.argument("location", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (var point : SpawnPoint.values()) {
                                builder.suggest(point.key().toLowerCase(Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            var key = StringArgumentType.getString(context, "location");
                            return SpawnPoint.byKey(key).map(point -> {
                                spawns.set(point, player.getLocation());
                                player.sendMessage(messages.get("command.rushset-set", Placeholder.unparsed("location", point.key())));
                                return Command.SINGLE_SUCCESS;
                            }).orElseGet(() -> help(player, messages));
                        }))
                .build();
    }

    private static int help(Player player, Messages messages) {
        messages.getLines("command.rushset-help").forEach(player::sendMessage);
        return Command.SINGLE_SUCCESS;
    }
}
