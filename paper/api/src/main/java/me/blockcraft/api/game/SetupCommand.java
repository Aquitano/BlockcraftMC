package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * /setup, the same command on every game server: staff stand where something belongs and name it. A game whose
 * places have names hands them to {@link #locations}; a game with more to set up builds on {@link #root}.
 */
public final class SetupCommand {

    private SetupCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("setup")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(GamePermissions.SETUP));
    }

    /** {@code /setup <key>} puts the place {@code key} names where the player stands; without one, it lists the keys. */
    public static <K> LiteralCommandNode<CommandSourceStack> locations(Messages gameMessages, List<K> places,
                                                                       Function<K, String> key, BiConsumer<K, Location> set) {
        var messages = BlockcraftApi.get().messages().prefixedLike(gameMessages);
        var keys = places.stream().map(key).toList();
        return root()
                .executes(context -> help(context.getSource().getPlayerOrThrow(), messages, keys))
                .then(Commands.argument("place", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            keys.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            String typed = StringArgumentType.getString(context, "place");
                            return places.stream().filter(place -> key.apply(place).equalsIgnoreCase(typed)).findFirst()
                                    .map(place -> {
                                        set.accept(place, player.getLocation());
                                        player.sendMessage(messages.get("setup.set", Placeholder.unparsed("place", key.apply(place))));
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .orElseGet(() -> help(player, messages, keys));
                        }))
                .build();
    }

    private static int help(Player player, Messages messages, List<String> keys) {
        player.sendMessage(messages.get("setup.help", Placeholder.unparsed("places", String.join(", ", keys))));
        return Command.SINGLE_SUCCESS;
    }
}
