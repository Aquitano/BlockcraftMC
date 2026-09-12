package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.menu.RushMenus;
import me.blockcraft.rush.storage.StatsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StatsCommand {

    private StatsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, StatsRepository stats, Messages messages) {
        return Commands.literal("stats")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    show(plugin, player, player, stats, messages);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            var target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(context.getSource()).getFirst();
                            show(plugin, context.getSource().getPlayerOrThrow(), target, stats, messages);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void show(Plugin plugin, Player viewer, Player target, StatsRepository stats, Messages messages) {
        Tasks.async(plugin, () -> stats.get(target.getUniqueId()), playerStats -> {
            viewer.sendMessage(Component.empty());
            if (viewer != target) {
                viewer.sendMessage(messages.get("stats.other-title", Placeholder.unparsed("player", target.getName())));
            }
            messages.getLines("stats.lines", RushMenus.statsPlaceholders(playerStats)).forEach(viewer::sendMessage);
            viewer.sendMessage(Component.empty());
        });
    }
}
