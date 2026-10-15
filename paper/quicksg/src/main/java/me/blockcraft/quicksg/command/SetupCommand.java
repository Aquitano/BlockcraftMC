package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.config.LocationKey;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class SetupCommand {

    private static final String HELP = "help";

    private SetupCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Locations<LocationKey> locations, Messages messages) {
        return Commands.literal("qsgset")
                .requires(source -> source.getSender() instanceof Player
                        && source.getSender().hasPermission("blockcraft.quicksg.admin"))
                .then(Commands.argument("location", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest(HELP);
                            Arrays.stream(LocationKey.values()).map(LocationKey::key).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrThrow();
                            String input = StringArgumentType.getString(context, "location");
                            if (input.equalsIgnoreCase(HELP)) {
                                messages.getLines("setup.help").forEach(player::sendMessage);
                                return Command.SINGLE_SUCCESS;
                            }
                            LocationKey.parse(input).ifPresentOrElse(key -> {
                                locations.set(key, player.getLocation());
                                player.sendMessage(messages.get("setup.set", Placeholder.unparsed("location", key.key())));
                            }, () -> player.sendMessage(messages.get("setup.unknown")));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
