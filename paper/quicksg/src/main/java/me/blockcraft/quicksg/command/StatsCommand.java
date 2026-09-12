package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.storage.StatsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
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
                    Player player = context.getSource().getPlayerOrThrow();
                    send(plugin, stats, messages, player, player, false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            Player viewer = context.getSource().getPlayerOrThrow();
                            Player target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(context.getSource()).getFirst();
                            send(plugin, stats, messages, viewer, target, true);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void send(Plugin plugin, StatsRepository stats, Messages messages, Player viewer, Player target, boolean withHeader) {
        Tasks.async(plugin, () -> stats.get(target.getUniqueId()), result -> {
            viewer.sendMessage(Component.empty());
            if (withHeader) {
                viewer.sendMessage(messages.get("stats.header", Placeholder.unparsed("player", target.getName())));
            }
            messages.getLines("stats.lines",
                    Formatter.number("wins", result.wins()),
                    Formatter.number("losses", result.losses()),
                    Formatter.number("deaths", result.deaths()),
                    Formatter.number("kills", result.kills()),
                    Formatter.number("points", result.points())).forEach(viewer::sendMessage);
            viewer.sendMessage(Component.empty());
        });
    }
}
