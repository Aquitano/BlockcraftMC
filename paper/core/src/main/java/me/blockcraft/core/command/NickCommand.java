package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.core.nick.Nicks;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Optional;

/** {@code /nick} wears a random name with a random one of the allowed ranks, {@code /nick <rank>} picks the rank. */
public final class NickCommand {

    private static final String PERMISSION = "blockcraft.nick";

    private NickCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Nicks nicks, Messages messages) {
        return Commands.literal("nick")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(PERMISSION))
                .executes(context -> assign(context, nicks, messages, Optional.empty()))
                .then(Commands.literal("off").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get(nicks.restore(player) ? "nick.removed" : "nick.none"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            nicks.ranks().stream().filter(rank -> rank.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String rank = StringArgumentType.getString(context, "rank");
                            if (!nicks.ranks().contains(rank)) {
                                context.getSource().getSender().sendMessage(messages.get("nick.unknown-rank",
                                        Placeholder.unparsed("ranks", String.join(", ", nicks.ranks()))));
                                return Command.SINGLE_SUCCESS;
                            }
                            return assign(context, nicks, messages, Optional.of(rank));
                        }))
                .build();
    }

    private static int assign(CommandContext<CommandSourceStack> context, Nicks nicks, Messages messages, Optional<String> rank)
            throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrThrow();
        player.sendMessage(messages.get("nick.loading"));
        nicks.assign(player, rank, nick -> player.sendMessage(nick
                .map(worn -> messages.get("nick.assigned", Placeholder.unparsed("nick", worn.name()), Placeholder.unparsed("rank", worn.rankGroup())))
                .orElseGet(() -> messages.get("nick.failed"))));
        return Command.SINGLE_SUCCESS;
    }
}
