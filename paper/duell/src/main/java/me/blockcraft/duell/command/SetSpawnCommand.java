package me.blockcraft.duell.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.game.DuelMode;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/** {@code /setspawn spawn} or {@code /setspawn <1vs1|SG|SJ><map>p<1|2>}, e.g. {@code /setspawn SG2p1}. */
public final class SetSpawnCommand {

    private static final String SPAWN = "spawn";
    private static final Pattern MAP_SPAWN = Pattern.compile("(?i)^(1vs1|SG|SJ)(\\d+)p([12])$");

    private SetSpawnCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Spawns spawns, DuellConfig config, Messages messages) {
        return Commands.literal("setspawn")
                .requires(source -> source.getSender() instanceof Player
                        && source.getSender().hasPermission("blockcraft.duell.setspawn"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest(SPAWN);
                            for (var mode : DuelMode.values()) {
                                for (int map = 1; map <= config.maps(mode).size(); map++) {
                                    builder.suggest(mode.setupKey() + map + "p1");
                                    builder.suggest(mode.setupKey() + map + "p2");
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrThrow();
                            String target = StringArgumentType.getString(context, "target");
                            if (target.equalsIgnoreCase(SPAWN)) {
                                spawns.setSpawn(player.getLocation());
                                player.sendMessage(messages.get("setspawn.spawn"));
                                return Command.SINGLE_SUCCESS;
                            }
                            var matcher = MAP_SPAWN.matcher(target);
                            if (!matcher.matches()) {
                                player.sendMessage(messages.get("setspawn.usage"));
                                return Command.SINGLE_SUCCESS;
                            }
                            var mode = DuelMode.bySetupKey(matcher.group(1)).orElseThrow();
                            int map = Integer.parseInt(matcher.group(2));
                            int slot = Integer.parseInt(matcher.group(3));
                            if (map < 1 || map > config.maps(mode).size()) {
                                player.sendMessage(messages.get("setspawn.usage"));
                                return Command.SINGLE_SUCCESS;
                            }
                            spawns.setMapSpawn(mode, map, slot, player.getLocation());
                            player.sendMessage(messages.get("setspawn.map",
                                    Placeholder.unparsed("mode", mode.setupKey()),
                                    Formatter.number("map", map),
                                    Formatter.number("player", slot)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
