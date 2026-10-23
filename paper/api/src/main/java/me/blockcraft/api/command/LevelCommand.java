package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.achievement.Achievements;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class LevelCommand {

    private LevelCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Xp xp, Achievements achievements, Messages messages) {
        return Commands.literal("level")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrThrow();
                    BlockcraftApi.get().profiles().progression(player, Optional.empty());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(source -> source.getSender().hasPermission("blockcraft.level.others"))
                        .executes(context -> {
                            Player target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(context.getSource()).getFirst();
                            context.getSource().getSender().sendMessage(messages.get("level.other",
                                    Placeholder.unparsed("player", target.getName()),
                                    Formatter.number("level", xp.level(target)),
                                    Formatter.number("xp", xp.total(target))));
                            context.getSource().getSender().sendMessage(achievements.nextCosmetic(xp.level(target)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
