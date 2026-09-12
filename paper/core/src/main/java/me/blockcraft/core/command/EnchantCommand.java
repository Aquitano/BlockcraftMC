package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.inventory.MenuType;

public final class EnchantCommand {

    private EnchantCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("enchant")
                .requires(CorePermission.player("enchant"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    MenuType.ENCHANTMENT.builder().location(player.getLocation()).checkReachable(false).build(player).open();
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
