package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            var target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(context.getSource()).getFirst();
                            send(plugin, stats, messages, context.getSource().getPlayerOrThrow(), target);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
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
