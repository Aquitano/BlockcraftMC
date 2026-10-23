package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Function;

/**
 * A clock of the player's own until reset or rejoin; the world and everyone else keep theirs. A player who may keep
 * it has their own choice stored, so {@link PlayerTimeListener} brings it back on every server.
 */
public final class PTimeCommand {

    private PTimeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("ptime")
                .requires(CorePermission.of("ptime"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("ptime.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(withTargets(Commands.literal("day"), context -> Optional.of(1000L), context -> messages.get("ptime.day"), messages))
                .then(withTargets(Commands.literal("noon"), context -> Optional.of(6000L), context -> messages.get("ptime.noon"), messages))
                .then(withTargets(Commands.literal("night"), context -> Optional.of(13000L), context -> messages.get("ptime.night"), messages))
                .then(withTargets(Commands.literal("midnight"), context -> Optional.of(18000L), context -> messages.get("ptime.midnight"), messages))
                .then(withTargets(Commands.literal("reset"), context -> Optional.empty(), context -> Component.empty(), messages))
                .then(withTargets(Commands.argument("ticks", IntegerArgumentType.integer(0, 23999)),
                        context -> Optional.of((long) IntegerArgumentType.getInteger(context, "ticks")),
                        context -> Component.text(IntegerArgumentType.getInteger(context, "ticks")), messages))
                .build();
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTargets(
            T node, Function<CommandContext<CommandSourceStack>, Optional<Long>> ticks,
            Function<CommandContext<CommandSourceStack>, Component> label, Messages messages) {
        return node
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("ptime.usage"))
                            .ifPresent(player -> apply(player, player, ticks.apply(context), label.apply(context), messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("ptime.others"))
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            for (var target : TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)) {
                                apply(sender, target, ticks.apply(context), label.apply(context), messages);
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void apply(CommandSender sender, Player target, Optional<Long> ticks, Component label, Messages messages) {
        ticks.ifPresentOrElse(time -> target.setPlayerTime(time, false), target::resetPlayerTime);
        boolean kept = target.equals(sender) && PlayerSettings.allowed(target, PlayerSetting.PLAYER_TIME);
        if (kept) {
            var settings = BlockcraftApi.get().settings();
            ticks.ifPresentOrElse(time -> settings.set(target, PlayerSetting.PLAYER_TIME, Long.toString(time)),
                    () -> settings.clear(target, PlayerSetting.PLAYER_TIME));
        }
        var time = Placeholder.component("time", label);
        var player = Placeholder.unparsed("player", target.getName());
        target.sendMessage(messages.get(ticks.isEmpty() ? "ptime.reset" : kept ? "ptime.set-kept" : "ptime.set", time));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get(ticks.isPresent() ? "ptime.set-other" : "ptime.reset-other", time, player));
        }
    }
}
