package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.nick.Nicks;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public final class NickCommand {

    private static final String PERMISSION = "blockcraft.nick";

    private NickCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Nicks nicks, Messages messages) {
        return Commands.literal("nick")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(PERMISSION))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get("nick.loading"));
                    nicks.assign(player, nick -> player.sendMessage(nick
                            .map(name -> messages.get("nick.assigned", Placeholder.unparsed("nick", name)))
                            .orElseGet(() -> messages.get("nick.failed"))));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("off").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get(nicks.restore(player) ? "nick.removed" : "nick.none"));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }
}
