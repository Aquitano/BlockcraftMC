package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;

public final class GamemodeCommand {

    private GamemodeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("gm")
                .requires(CorePermission.player("gm"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("gm.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(mode(messages, "0", GameMode.SURVIVAL, "Survival"))
                .then(mode(messages, "1", GameMode.CREATIVE, "Creative"))
                .then(mode(messages, "2", GameMode.ADVENTURE, "Adventure"))
                .then(mode(messages, "3", GameMode.SPECTATOR, "Spectator"))
                .build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mode(Messages messages, String literal, GameMode mode, String label) {
        return Commands.literal(literal).executes(context -> {
            var player = context.getSource().getPlayerOrThrow();
            player.setGameMode(mode);
            player.sendMessage(messages.get("gm.changed", Placeholder.unparsed("mode", label)));
            return Command.SINGLE_SUCCESS;
        });
    }
}
