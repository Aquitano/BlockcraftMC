package me.blockcraft.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

final class CorePermission {

    private CorePermission() {
    }

    static Predicate<CommandSourceStack> player(String command) {
        return source -> source.getSender() instanceof Player player && player.hasPermission("blockcraft.core." + command);
    }
}
