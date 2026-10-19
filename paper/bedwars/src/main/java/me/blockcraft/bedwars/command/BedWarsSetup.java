package me.blockcraft.bedwars.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.bedwars.lobby.LobbyLayout;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.bedwars.map.ArenaWorlds;
import me.blockcraft.bedwars.map.BedSpot;
import me.blockcraft.bedwars.map.Border;
import me.blockcraft.bedwars.map.MapPool;
import me.blockcraft.bedwars.map.Point;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.team.TeamColor;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.BiFunction;

/**
 * BedWars' /setup: places what a BedWars map needs. The lobby's spots are stored in its {@code lobby.yml} and
 * published with {@code /map save}. A pool map is opened with {@code edit}, which loads its world and takes the
 * builder there; every other step is taken standing in that world and lands in its {@code map.yml} at once, and
 * {@code save} copies the world itself back into the pool.
 */
public final class BedWarsSetup {

    private static final int BED_REACH = 5;
    private static final double REMOVE_REACH_SQUARED = 4.0;
    private static final Point NEW_MAP_START = new Point(0.5, 100.0, 0.5, 0.0f, 0.0f);

    private final MapPool pool;
    private final LobbyLayout layout;
    private final List<TeamColor> teams;
    private final Messages messages;

    private BedWarsSetup(MapPool pool, LobbyLayout layout, List<TeamColor> teams, Messages messages) {
        this.pool = pool;
        this.layout = layout;
        this.teams = teams;
        this.messages = messages;
    }

    public static LiteralCommandNode<CommandSourceStack> create(MapPool pool, LobbyLayout layout, List<TeamColor> teams,
                                                                Messages messages) {
        return new BedWarsSetup(pool, layout, teams, messages).tree();
    }

    private LiteralCommandNode<CommandSourceStack> tree() {
        return SetupCommand.root()
                .executes(context -> help(player(context)))
                .then(lobby())
                .then(Commands.literal("map").then(map()))
                .build();
    }

    private ArgumentBuilder<CommandSourceStack, ?> map() {
        return Commands.argument("map", StringArgumentType.word())
                .suggests((context, builder) -> {
                    pool.maps().forEach(map -> builder.suggest(map.key()));
                    return builder.buildFuture();
                })
                .then(Commands.literal("edit").executes(context -> edit(player(context), map(context))))
                .then(Commands.literal("save").executes(context -> save(player(context), map(context))))
                .then(Commands.literal("info").executes(context -> info(player(context), map(context))))
                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> change(context, (map, player) -> map.withName(StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("icon").executes(context -> change(context,
                        (map, player) -> map.withIcon(player.getInventory().getItemInMainHand().getType()))))
                .then(Commands.literal("spectator").executes(context -> change(context,
                        (map, player) -> map.withSpectator(Point.of(player.getLocation())))))
                .then(Commands.literal("shop").executes(context -> change(context,
                        (map, player) -> map.withShops(adding(map.shops(), Point.of(player.getLocation()))))))
                .then(Commands.literal("spawn").then(teamArgument().executes(context -> change(context,
                        (map, player) -> map.withSpawn(team(context), Point.of(player.getLocation()))))))
                .then(Commands.literal("bed").then(teamArgument().executes(this::bed)))
                .then(Commands.literal("spawner").then(spawner()))
                .then(Commands.literal("border").then(border()))
                .then(Commands.literal("remove").executes(this::remove));
    }

    private ArgumentBuilder<CommandSourceStack, ?> spawner() {
        return Commands.argument("resource", StringArgumentType.word())
                .suggests((context, builder) -> {
                    Arrays.stream(Resource.values()).forEach(resource -> builder.suggest(resource.key()));
                    return builder.buildFuture();
                })
                .executes(context -> {
                    var resource = Resource.byKey(StringArgumentType.getString(context, "resource"));
                    if (resource.isEmpty()) {
                        return help(player(context));
                    }
                    var spot = blockCenter(player(context).getLocation());
                    return change(context, (map, player) -> map.withSpawners(resource.get(), adding(map.spawners(resource.get()), spot)));
                });
    }

    private ArgumentBuilder<CommandSourceStack, ?> border() {
        return Commands.argument("x1", IntegerArgumentType.integer())
                .then(Commands.argument("z1", IntegerArgumentType.integer())
                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                        .executes(context -> change(context, (map, player) -> map.withBorder(Border.between(
                                                IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "z1"),
                                                IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "z2"))))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> lobby() {
        var lobby = Commands.literal("lobby");
        for (var spot : List.of(LobbyLayout.SPAWN, LobbyLayout.CPS_TESTER, LobbyLayout.JUMP_START, LobbyLayout.JUMP_FINISH)) {
            lobby.then(Commands.literal(spot.replace("jump-and-run.", "jump-")).executes(context -> setLobby(player(context), spot)));
        }
        lobby.then(Commands.literal("joiner").then(teamArgument()
                .executes(context -> setLobby(player(context), LobbyLayout.joinerKey(team(context))))));
        lobby.then(Commands.literal("display").then(teamArgument().then(Commands.argument("index", IntegerArgumentType.integer(0))
                .executes(context -> setLobby(player(context),
                        LobbyLayout.displayKey(team(context), IntegerArgumentType.getInteger(context, "index")))))));
        return lobby;
    }

    private int setLobby(Player player, String key) {
        layout.set(key, player.getLocation());
        player.sendMessage(messages.get("setup.lobby-set", Placeholder.unparsed("spot", key)));
        return Command.SINGLE_SUCCESS;
    }

    private int edit(Player player, String key) {
        if (!MapPool.isValidKey(key)) {
            player.sendMessage(messages.get("setup.invalid-key"));
            return Command.SINGLE_SUCCESS;
        }
        var map = pool.map(key).orElseGet(() -> {
            var created = ArenaMap.empty(key);
            pool.save(created);
            return created;
        });
        if (ArenaWorlds.loaded(key).isEmpty() && Files.isDirectory(pool.world(key))) {
            ArenaWorlds.copy(pool, key);
        }
        var world = ArenaWorlds.load(key);
        player.teleport(map.spectator().orElse(NEW_MAP_START).in(world));
        player.sendMessage(messages.get("setup.editing", Placeholder.unparsed("map", key)));
        return Command.SINGLE_SUCCESS;
    }

    private int save(Player player, String key) {
        var world = ArenaWorlds.loaded(key);
        if (world.isEmpty()) {
            player.sendMessage(messages.get("setup.not-loaded"));
            return Command.SINGLE_SUCCESS;
        }
        ArenaWorlds.publish(pool, world.get(), key);
        player.sendMessage(messages.get("setup.saved", Placeholder.unparsed("map", key)));
        return Command.SINGLE_SUCCESS;
    }

    private int info(Player player, String key) {
        var missing = pool.map(key).map(map -> map.missing(teams)).orElse(List.of("map.yml"));
        player.sendMessage(missing.isEmpty()
                ? messages.get("setup.ready", Placeholder.unparsed("map", key))
                : messages.get("setup.missing", Placeholder.unparsed("map", key), Placeholder.unparsed("missing", String.join(", ", missing))));
        return Command.SINGLE_SUCCESS;
    }

    private int bed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = player(context);
        var block = player.getTargetBlockExact(BED_REACH);
        if (block == null || !(block.getBlockData() instanceof Bed bed)) {
            player.sendMessage(messages.get("setup.no-bed"));
            return Command.SINGLE_SUCCESS;
        }
        var foot = bed.getPart() == Bed.Part.FOOT ? block : block.getRelative(bed.getFacing().getOppositeFace());
        var spot = new BedSpot(foot.getX(), foot.getY(), foot.getZ(), bed.getFacing());
        return change(context, (map, editor) -> map.withBed(team(context), spot));
    }

    private int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var here = player(context).getLocation();
        return change(context, (map, player) -> {
            var changed = map.withShops(without(map.shops(), here));
            for (var resource : Resource.values()) {
                changed = changed.withSpawners(resource, without(changed.spawners(resource), here));
            }
            return changed;
        });
    }

    /** Applies {@code edit} to the map the player stands in, and saves its map.yml. */
    private int change(CommandContext<CommandSourceStack> context, BiFunction<ArenaMap, Player, ArenaMap> edit) throws CommandSyntaxException {
        var player = player(context);
        var key = map(context);
        if (!player.getWorld().getKey().equals(ArenaWorlds.key(key))) {
            player.sendMessage(messages.get("setup.not-in-map", Placeholder.unparsed("map", key)));
            return Command.SINGLE_SUCCESS;
        }
        var changed = edit.apply(pool.map(key).orElseGet(() -> ArenaMap.empty(key)), player);
        pool.save(changed);
        player.sendMessage(messages.get("setup.changed", Placeholder.unparsed("map", key)));
        return Command.SINGLE_SUCCESS;
    }

    private int help(Player player) {
        messages.getLines("setup.help").forEach(player::sendMessage);
        return Command.SINGLE_SUCCESS;
    }

    private ArgumentBuilder<CommandSourceStack, ?> teamArgument() {
        return Commands.argument("team", StringArgumentType.word()).suggests((context, builder) -> {
            teams.forEach(team -> builder.suggest(team.key()));
            return builder.buildFuture();
        });
    }

    private static TeamColor team(CommandContext<CommandSourceStack> context) {
        var key = StringArgumentType.getString(context, "team");
        return TeamColor.byKey(key).orElseThrow(() -> new IllegalArgumentException("Unknown team " + key));
    }

    private static String map(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "map");
    }

    private static Player player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrThrow();
    }

    private static Point blockCenter(Location location) {
        return new Point(location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5, 0.0f, 0.0f);
    }

    private static List<Point> adding(List<Point> points, Point point) {
        var changed = new ArrayList<>(points);
        changed.add(point);
        return changed;
    }

    /** The points, less the nearest one within two blocks of {@code here}. */
    private static List<Point> without(List<Point> points, Location here) {
        Function<Point, Double> distance = point -> point.in(here.getWorld()).distanceSquared(here);
        var nearest = points.stream().filter(point -> distance.apply(point) <= REMOVE_REACH_SQUARED)
                .min(Comparator.comparing(distance));
        return nearest.map(point -> points.stream().filter(other -> other != point).toList()).orElse(points);
    }
}
