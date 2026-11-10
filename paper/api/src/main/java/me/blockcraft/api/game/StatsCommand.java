package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** {@code /stats [player]} from the module's {@code stats.header} and {@code stats.lines}. */
public final class StatsCommand {

    private StatsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, StatsRepository stats, Messages messages) {
        return Commands.literal("stats")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    send(plugin, stats, messages, player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (context.getSource().getSender() instanceof Player viewer) {
                                shownTo(viewer).map(Player::getName)
                                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                        .forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var viewer = context.getSource().getPlayerOrThrow();
                            String name = StringArgumentType.getString(context, "player");
                            find(viewer, name).ifPresentOrElse(
                                    target -> send(plugin, stats, messages, viewer, target),
                                    () -> viewer.sendMessage(BlockcraftApi.get().messages().prefixedLike(messages)
                                            .get("stats.not-here", Placeholder.unparsed("player", name))));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /**
     * Paper's player argument matches the name a player logged in with, so it would find a nicked player by their
     * real name and miss the nick everyone sees; {@link Player#getName()} is the nick.
     */
    private static Optional<? extends Player> find(Player viewer, String name) {
        return shownTo(viewer).filter(player -> player.getName().equalsIgnoreCase(name)).findFirst();
    }

    private static Stream<? extends Player> shownTo(Player viewer) {
        return Bukkit.getOnlinePlayers().stream().filter(viewer::canSee);
    }

    private static void send(Plugin plugin, StatsRepository stats, Messages messages, Player viewer, Player target) {
        Tasks.async(plugin, () -> stats.get(target.getUniqueId()), loaded -> {
            var result = viewer.equals(target) ? loaded : BlockcraftApi.get().nickStats().stats(target, loaded);
            viewer.sendMessage(Component.empty());
            if (!viewer.equals(target)) {
                viewer.sendMessage(messages.get("stats.header", Placeholder.unparsed("player", target.getName())));
            }
            messages.getLines("stats.lines", result.placeholders()).forEach(viewer::sendMessage);
            viewer.sendMessage(Component.empty());
        });
    }
}
