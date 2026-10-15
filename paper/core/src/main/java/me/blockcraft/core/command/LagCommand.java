package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Locale;

public final class LagCommand {

    private static final long MEBIBYTE = 1024L * 1024L;

    private LagCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        var durations = new DurationText(messages);
        return Commands.literal("lag")
                .requires(CorePermission.player("lag"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    var api = BlockcraftApi.get();
                    var tps = Bukkit.getTPS();
                    var runtime = Runtime.getRuntime();
                    var uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
                    messages.getLines("lag.lines",
                                    Placeholder.unparsed("server", api.server().name()),
                                    Placeholder.unparsed("group", api.server().group()),
                                    Placeholder.unparsed("state", api.presence().state().name()),
                                    decimal("tps1", Math.min(20, tps[0])),
                                    decimal("tps5", Math.min(20, tps[1])),
                                    decimal("tps15", Math.min(20, tps[2])),
                                    decimal("mspt", Bukkit.getAverageTickTime()),
                                    Placeholder.unparsed("uptime", durations.format(uptime)),
                                    Formatter.number("used", (runtime.totalMemory() - runtime.freeMemory()) / MEBIBYTE),
                                    Formatter.number("max", runtime.maxMemory() / MEBIBYTE),
                                    Formatter.number("online", Bukkit.getOnlinePlayers().size()),
                                    Formatter.number("slots", Bukkit.getMaxPlayers()))
                            .forEach(player::sendMessage);
                    for (var world : Bukkit.getWorlds()) {
                        player.sendMessage(messages.get("lag.world",
                                Placeholder.unparsed("world", world.getName()),
                                Formatter.number("chunks", world.getChunkCount()),
                                Formatter.number("entities", world.getEntityCount())));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static TagResolver decimal(String key, double value) {
        return Placeholder.unparsed(key, String.format(Locale.GERMANY, "%.1f", value));
    }
}
