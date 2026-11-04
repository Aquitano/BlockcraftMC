package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Optional;

/**
 * A clock of the player's own until reset or rejoin; the world and everyone else keep theirs. A player who may keep
 * it has their own choice stored, so {@link PlayerTimeListener} brings it back on every server.
 */
public final class PTimeCommand {

    private PTimeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        var targets = new SelfOrOthers("ptime", messages);
        return Commands.literal("ptime")
                .requires(CorePermission.of("ptime"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("ptime.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(targets.executes(Commands.literal("day"), time(Optional.of(1000L), messages.get("ptime.day"), messages)))
                .then(targets.executes(Commands.literal("noon"), time(Optional.of(6000L), messages.get("ptime.noon"), messages)))
                .then(targets.executes(Commands.literal("night"), time(Optional.of(13000L), messages.get("ptime.night"), messages)))
                .then(targets.executes(Commands.literal("midnight"), time(Optional.of(18000L), messages.get("ptime.midnight"), messages)))
                .then(targets.executes(Commands.literal("reset"), time(Optional.empty(), Component.empty(), messages)))
                .then(targets.executes(Commands.argument("ticks", IntegerArgumentType.integer(0, 23999)), context -> {
                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                    return Optional.of(time(Optional.of((long) ticks), Component.text(ticks), messages));
                }))
                .build();
    }

    private static SelfOrOthers.Action time(Optional<Long> ticks, Component label, Messages messages) {
        return (sender, target) -> {
            ticks.ifPresentOrElse(time -> target.setPlayerTime(time, false), target::resetPlayerTime);
            boolean kept = target.equals(sender) && PlayerSettings.allowed(target, PlayerSetting.PLAYER_TIME);
            if (kept) {
                var settings = BlockcraftApi.get().settings();
                ticks.ifPresentOrElse(time -> settings.set(target, PlayerSetting.PLAYER_TIME, Long.toString(time)),
                        () -> settings.clear(target, PlayerSetting.PLAYER_TIME));
            }
            var time = Placeholder.component("time", label);
            target.sendMessage(messages.get(ticks.isEmpty() ? "ptime.reset" : kept ? "ptime.set-kept" : "ptime.set", time));
            return SelfOrOthers.Outcome.told(ticks.isPresent() ? "ptime.set-other" : "ptime.reset-other", time);
        };
    }
}
