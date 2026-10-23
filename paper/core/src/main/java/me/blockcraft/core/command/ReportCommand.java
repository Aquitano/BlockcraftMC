package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest.SubmitReport;
import me.blockcraft.common.player.PlayerReference;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The player's side of /report: the proxy forwards {@code /report <player>} here, {@link ReportDialog} collects the
 * reason, and the finished report goes back to the proxy as a {@link SubmitReport}, which resolves the name the
 * player sees, stores the report, applies the cooldown and tells the staff.
 */
public final class ReportCommand {

    private ReportCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(List<String> reasons, Messages messages) {
        return Commands.literal("report")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("report.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            String target = StringArgumentType.getString(context, "player");
                            if (!PlayerReference.isName(target)) {
                                player.sendMessage(messages.get("report.invalid-player"));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (target.equalsIgnoreCase(BlockcraftApi.get().realName(player))) {
                                player.sendMessage(messages.get("report.self"));
                                return Command.SINGLE_SUCCESS;
                            }
                            new ReportDialog(target, reasons, messages).open(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
