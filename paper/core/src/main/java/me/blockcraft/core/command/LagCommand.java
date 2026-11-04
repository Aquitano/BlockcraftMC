package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

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
                                    Placeholder.unparsed("paper", Bukkit.getVersion()),
                                    Placeholder.unparsed("java", Runtime.version().toString()),
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
                .then(Commands.literal("plugins")
                        .executes(context -> {
                            listPlugins(context.getSource().getPlayerOrThrow(), messages);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    plugins().stream().map(Plugin::getName)
                                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    showPlugin(context.getSource().getPlayerOrThrow(), messages,
                                            StringArgumentType.getString(context, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private static void listPlugins(Player player, Messages messages) {
        var plugins = plugins();
        player.sendMessage(messages.get("lag.plugins.title", Formatter.number("count", plugins.size()),
                Placeholder.unparsed("server", BlockcraftApi.get().server().name())));
        for (var plugin : plugins) {
            var meta = plugin.getPluginMeta();
            player.sendMessage(messages.get("lag.plugins.entry",
                            Placeholder.unparsed("plugin", meta.getName()),
                            Placeholder.unparsed("version", meta.getVersion()),
                            Placeholder.component("status", status(plugin, messages)))
                    .clickEvent(ClickEvent.runCommand("/serverinfo plugins " + meta.getName()))
                    .hoverEvent(messages.get("lag.plugins.hint")));
        }
    }

    private static void showPlugin(Player player, Messages messages, String name) {
        var plugins = plugins();
        var found = plugins.stream().filter(plugin -> plugin.getName().equalsIgnoreCase(name)).findFirst();
        if (found.isEmpty()) {
            player.sendMessage(messages.get("lag.plugins.unknown", Placeholder.unparsed("plugin", name)));
            return;
        }
        var plugin = found.orElseThrow();
        var meta = plugin.getPluginMeta();
        var metadata = plugins.stream().map(Plugin::getPluginMeta).toList();
        messages.getLines("lag.plugins.details",
                        Placeholder.unparsed("plugin", meta.getName()),
                        Placeholder.unparsed("version", meta.getVersion()),
                        Placeholder.component("status", status(plugin, messages)),
                        Placeholder.component("required", names(meta.getPluginDependencies(), messages)),
                        Placeholder.component("optional", names(meta.getPluginSoftDependencies(), messages)),
                        Placeholder.component("required_by", names(dependents(meta, metadata, PluginMeta::getPluginDependencies), messages)),
                        Placeholder.component("optional_for", names(dependents(meta, metadata, PluginMeta::getPluginSoftDependencies), messages)))
                .forEach(player::sendMessage);
    }

    static List<String> dependents(PluginMeta target, List<PluginMeta> plugins, Function<PluginMeta, List<String>> dependencies) {
        var names = new ArrayList<>(target.getProvidedPlugins());
        names.add(target.getName());
        return plugins.stream()
                .filter(plugin -> !plugin.getName().equalsIgnoreCase(target.getName()))
                .filter(plugin -> dependencies.apply(plugin).stream().anyMatch(dependency -> names.stream().anyMatch(dependency::equalsIgnoreCase)))
                .map(PluginMeta::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<Plugin> plugins() {
        return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .sorted(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static Component status(Plugin plugin, Messages messages) {
        return messages.get(plugin.isEnabled() ? "lag.plugins.enabled" : "lag.plugins.disabled");
    }

    private static Component names(List<String> names, Messages messages) {
        return names.isEmpty() ? messages.get("lag.plugins.none")
                : Component.text(String.join(", ", names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
    }

    private static TagResolver decimal(String key, double value) {
        return Placeholder.unparsed(key, String.format(Locale.GERMANY, "%.1f", value));
    }
}
