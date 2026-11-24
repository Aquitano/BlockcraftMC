package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Lists the commands registered on this server that the player may run, each with its own description. */
public final class TeamCommand {

    private TeamCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("team")
                .requires(CorePermission.of("team"))
                .executes(context -> help(context.getSource(), messages))
                .then(Commands.literal("help").executes(context -> help(context.getSource(), messages)))
                .build();
    }

    private static int help(CommandSourceStack source, Messages messages) {
        var sender = source.getSender();
        if (sender instanceof Player player) {
            new TeamHelpDialog(source, messages).open(player, 0);
            return Command.SINGLE_SUCCESS;
        }
        messages.getLines("team.help-header").forEach(sender::sendMessage);
        for (var entry : CommandRegistry.usableBy(source)) {
            sender.sendMessage(messages.get("team.help-entry",
                    Placeholder.unparsed("command", entry.name()), Placeholder.component("description", entry.description())));
        }
        messages.getLines("team.help-footer").forEach(sender::sendMessage);
        return Command.SINGLE_SUCCESS;
    }
}
