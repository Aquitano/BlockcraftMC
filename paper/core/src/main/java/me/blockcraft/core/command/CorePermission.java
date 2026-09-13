package me.blockcraft.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

final class CorePermission {

    static final String BYPASS_RANK = "blockcraft.core.bypass-rank";

    private CorePermission() {
    }

    static Predicate<CommandSourceStack> player(String command) {
        return source -> source.getSender() instanceof Player player && player.hasPermission("blockcraft.core." + command);
    }
}
