package me.blockcraft.core.command;

import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Keeps staff from using player commands on someone who is their equal or above, and from silly self-targeting. */
final class TargetGuard {

    private TargetGuard() {
    }

    static boolean mayTarget(CommandSender sender, Player target, Messages messages) {
        if (!(sender instanceof Player actor) || actor.equals(target)
                || actor.hasPermission(CorePermission.BYPASS_RANK) || Ranks.outranks(actor, target)) {
            return true;
        }
        sender.sendMessage(messages.get("rank.protected"));
        return false;
    }

    static boolean notSelf(CommandSender sender, Player target, Messages messages) {
        if (!target.equals(sender)) {
            return true;
        }
        sender.sendMessage(messages.get("rank.self"));
        return false;
    }
}
