package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Comparator;

/** The players the sender can see within a radius, nearest first. */
public final class NearCommand {

    public record Radius(int fallback, int max) {
    }

    private NearCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Radius radius, Messages messages) {
        return Commands.literal("near")
                .requires(CorePermission.player("near"))
                .executes(context -> list(context.getSource().getPlayerOrThrow(), radius.fallback(), messages))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, radius.max()))
                        .executes(context -> list(context.getSource().getPlayerOrThrow(),
                                IntegerArgumentType.getInteger(context, "radius"), messages)))
                .build();
    }

    private static int list(Player viewer, int radius, Messages messages) {
        var here = viewer.getLocation();
        var near = viewer.getWorld().getPlayers().stream()
                .filter(other -> !other.equals(viewer) && viewer.canSee(other))
                .filter(other -> other.getLocation().distanceSquared(here) <= (double) radius * radius)
                .sorted(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(here)))
                .toList();
        if (near.isEmpty()) {
            viewer.sendMessage(messages.get("near.none", Formatter.number("radius", radius)));
            return Command.SINGLE_SUCCESS;
        }
        viewer.sendMessage(messages.get("near.header", Formatter.number("radius", radius), Formatter.number("count", near.size())));
        for (var other : near) {
            viewer.sendMessage(messages.get("near.entry", Placeholder.unparsed("player", other.getName()),
                    Formatter.number("distance", Math.round(other.getLocation().distance(here)))));
        }
        return Command.SINGLE_SUCCESS;
    }
}
