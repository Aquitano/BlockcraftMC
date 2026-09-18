package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.elo.Elo;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.elo.EloEntry;
import me.blockcraft.common.elo.EloRating;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class EloCommand {

    private static final int TOP_SIZE = 10;

    private EloCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Elo elo, Messages messages) {
        return Commands.literal("elo")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    var uuid = player.getUniqueId();
                    Tasks.async(plugin, () -> elo.rating(uuid),
                            rating -> player.sendMessage(messages.get("elo.self", describe(elo, rating))));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("top")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            Tasks.async(plugin, () -> elo.top(TOP_SIZE), entries -> sendTop(player, elo, messages, entries));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            String name = StringArgumentType.getString(context, "player");
                            var target = Placeholder.unparsed("player", name);
                            Tasks.async(plugin, () -> elo.ratingByName(name), rating -> player.sendMessage(rating
                                    .map(found -> messages.get("elo.other", describe(elo, found), target))
                                    .orElseGet(() -> messages.get("elo.unknown", target))));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static TagResolver describe(Elo elo, EloRating rating) {
        return TagResolver.resolver(elo.placeholders(rating),
                Formatter.number("wins", rating.wins()),
                Formatter.number("losses", rating.losses()),
                Placeholder.unparsed("season", elo.season().key()));
    }

    private static void sendTop(Player player, Elo elo, Messages messages, List<EloEntry> entries) {
        player.sendMessage(messages.get("elo.top.separator"));
        player.sendMessage(messages.get("elo.top.title", Placeholder.unparsed("season", elo.season().key())));
        if (entries.isEmpty()) {
            player.sendMessage(messages.get("elo.top.empty"));
        }
        int rank = 0;
        for (var entry : entries) {
            rank++;
            player.sendMessage(messages.get(rank == 1 ? "elo.top.first" : "elo.top.entry",
                    Formatter.number("rank", rank),
                    Placeholder.unparsed("name", entry.name()),
                    Formatter.number("rating", entry.rating()),
                    Placeholder.component("group", elo.groupName(entry.rating()))));
        }
        player.sendMessage(messages.get("elo.top.separator"));
    }
}
