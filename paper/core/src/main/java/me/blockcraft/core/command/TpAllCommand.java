package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /tpall [player]}: everyone the sender may move goes to the sender, or to the named player. */
public final class TpAllCommand {

    private TpAllCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(BackLocations back, StaffAudit audit, Messages messages) {
        return Commands.literal("tpall")
                .requires(CorePermission.of("tpall"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("tpall.usage"))
                            .ifPresent(player -> gather(player, player, back, audit, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            gather(context.getSource().getSender(), Players.resolve(context, "player"), back, audit, messages);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void gather(CommandSender sender, Player destination, BackLocations back, StaffAudit audit, Messages messages) {
        List<Player> others = Bukkit.getOnlinePlayers().stream()
                .filter(online -> !online.equals(destination))
                .map(Player.class::cast)
                .toList();
        long moved = TargetGuard.permitted(sender, others, messages).stream()
                .filter(target -> back.teleport(target, destination.getLocation()))
                .count();
        if (destination.equals(sender)) {
            audit.record(sender, "tpall", String.valueOf(moved));
        } else {
            audit.record(sender, "tpall", destination, String.valueOf(moved));
        }
        sender.sendMessage(messages.get("tpall.done", Formatter.number("count", moved),
                Placeholder.unparsed("player", destination.getName())));
    }
}
