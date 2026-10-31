package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.activity.MapSecrets;
import me.blockcraft.lobby.config.ActivitiesConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class LobbySetCommand {

    private LobbySetCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Locations<String> locations, Messages messages,
                                                               ActivitiesConfig config, Locations<LobbyLocation> warps,
                                                               List<String> optionalSpots, List<String> optionalBlocks,
                                                               Runnable refresh) {
        var root = Commands.literal("lobbyset").requires(source -> source.getSender().hasPermission(LobbyPermissions.SET_SPAWN));
        var keys = new ArrayList<>(List.of("pond", "parkour-start", "parkour-finish", "parkour-top", "parkour-finish-sign", "santa", "prisoner"));
        keys.addAll(MapSecrets.SPOTS);
        for (int i = 1; i <= config.checkpoints(); i++) {
            keys.add("checkpoint-" + i);
        }
        for (int i = 1; i <= config.easterSpots(); i++) {
            keys.add("egg-" + i);
        }
        var settable = new ArrayList<>(keys);
        settable.addAll(optionalSpots);
        settable.addAll(optionalBlocks);
        for (var key : settable) {
            root.then(Commands.literal(key).requires(source -> source.getSender() instanceof Player).executes(context -> {
                var player = context.getSource().getPlayerOrThrow();
                var point = player.getLocation();
                if (key.equals("parkour-finish-sign")) {
                    var target = player.getTargetBlockExact(10);
                    if (target == null || !(target.getState() instanceof Sign)) {
                        player.sendMessage(messages.get("activities.setup-sign-target"));
                        return 0;
                    }
                    point = target.getLocation();
                } else if (MapSecrets.BLOCK_SPOTS.contains(key) || optionalBlocks.contains(key)) {
                    var target = player.getTargetBlockExact(10);
                    if (target == null) {
                        player.sendMessage(messages.get("activities.setup-block-target"));
                        return 0;
                    }
                    point = target.getLocation();
                    point.setYaw(player.getYaw());
                }
                locations.set(key, point);
                player.sendMessage(messages.get("activities.location-set", Placeholder.unparsed("location", key)));
                refresh.run();
                return Command.SINGLE_SUCCESS;
            }));
        }
        root.then(Commands.literal("check").executes(context -> {
            var problems = new ArrayList<Component>();
            var start = locations.get("parkour-start");
            var plates = new HashSet<Block>();
            for (var key : keys) {
                if (key.startsWith("egg-")) {
                    continue;
                }
                var point = locations.get(key);
                var label = Placeholder.unparsed("location", key);
                if (key.equals("parkour-finish-sign")) {
                    if (point.isPresent() && !(point.orElseThrow().getBlock().getState() instanceof Sign)) {
                        problems.add(messages.get("activities.setup-sign-target"));
                    }
                    continue;
                }
                if (point.isEmpty()) {
                    problems.add(messages.get("activities.setup-missing", label));
                } else if (key.equals("parkour-start") || key.equals("parkour-finish") || key.startsWith("checkpoint-")) {
                    var block = point.orElseThrow().getBlock();
                    if (!Tag.PRESSURE_PLATES.isTagged(block.getType())) {
                        problems.add(messages.get("activities.setup-plate", label));
                    }
                    if (!plates.add(block)) {
                        problems.add(messages.get("activities.setup-duplicate", label));
                    }
                    if (start.isPresent() && !start.orElseThrow().getWorld().equals(block.getWorld())) {
                        problems.add(messages.get("activities.setup-world", label));
                    }
                }
            }
            if (warps.get(LobbyLocation.JUMP).isEmpty()) {
                problems.add(messages.get("activities.setup-jump"));
            }
            var eggs = new HashSet<Location>();
            for (int i = 1; i <= config.easterSpots(); i++) {
                var key = "egg-" + i;
                locations.get(key).ifPresent(point -> {
                    var block = new Location(point.getWorld(), point.getBlockX(), point.getBlockY(), point.getBlockZ());
                    if (!eggs.add(block)) {
                        problems.add(messages.get("activities.setup-egg-duplicate", Placeholder.unparsed("location", key)));
                    }
                });
            }
            if (eggs.size() < config.easterEggs()) {
                problems.add(messages.get("activities.setup-eggs", Formatter.number("found", eggs.size()),
                        Formatter.number("required", config.easterEggs())));
            }
            var sender = context.getSource().getSender();
            problems.forEach(sender::sendMessage);
            sender.sendMessage(problems.isEmpty() ? messages.get("activities.setup-ready")
                    : messages.get("activities.setup-problems", Formatter.number("count", problems.size())));
            return problems.isEmpty() ? Command.SINGLE_SUCCESS : 0;
        }));
        return root.build();
    }
}
