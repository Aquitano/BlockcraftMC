package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SpeedCommand {

    private static final int AMPLIFIER = 3;

    private SpeedCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("speed")
                .requires(CorePermission.player("speed"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        player.sendMessage(messages.get("speed.disabled"));
                    } else {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, AMPLIFIER));
                        player.sendMessage(messages.get("speed.enabled"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
