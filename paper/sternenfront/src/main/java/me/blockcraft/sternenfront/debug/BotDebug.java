package me.blockcraft.sternenfront.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.appearance.RigSnapshot;
import me.blockcraft.sternenfront.bot.BotSnapshot;
import me.blockcraft.sternenfront.bot.BotSnapshot.Point;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class BotDebug implements Listener, AutoCloseable {
    private record Block(int x, int y, int z, String data) { }
    private record Nearby(UUID id, String type, Point position, Point velocity, boolean collidable) { }
    private record Frame(long serverTick, long gameTick, long nanoTime, double averageTickMillis,
                         BotSnapshot bot, RigSnapshot rig, List<Block> blocks, List<Nearby> nearby) { }
    private record Request(UUID reporter, UUID actor, String file, String started, long markedTick,
                           Point observer, float yaw, float pitch, String world,
                           String server, String plugin, String java, String config) { }
    private record Report(int format, Request request, String finished, String reason, List<Frame> frames) { }
    private record Written(String file, String error) { }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final double REACH = 6;
    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<SternenfrontGame> game;
    private final Set<UUID> observers = new HashSet<>();
    private final Map<UUID, BotTrace<Frame>> traces = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final int historyTicks;
    private final int captureTicks;
    private final int cooldownTicks;
    private final BukkitTask task;

    public BotDebug(Plugin plugin, Messages messages, Supplier<SternenfrontGame> game) {
        this.plugin = plugin;
        this.messages = messages;
        this.game = game;
        var read = BundledDefaults.strict(plugin.getConfig());
        historyTicks = Math.clamp(read.integer("debug.history-ticks"), 20, 200);
        captureTicks = Math.clamp(read.integer("debug.capture-ticks"), 20, 600);
        cooldownTicks = Math.clamp(read.integer("debug.cooldown-ticks"), 20, 1200);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private static boolean permitted(Player player) {
        return player.isOp() || player.hasPermission("blockcraft.sternenfront.debug");
    }

    public boolean enabled(Player player) {
        return observers.contains(player.getUniqueId()) && permitted(player);
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("sfdebug")
                .requires(source -> source.getSender() instanceof Player player && permitted(player))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (!observers.remove(player.getUniqueId())) {
                        observers.add(player.getUniqueId());
                    }
                    player.sendMessage(messages.get(enabled(player) ? "debug.enabled" : "debug.disabled",
                            Formatter.number("history", historyTicks / 20), Formatter.number("seconds", (captureTicks + 19) / 20)));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("off").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    observers.remove(player.getUniqueId());
                    player.sendMessage(messages.get("debug.disabled"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("capture").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    observers.add(player.getUniqueId());
                    inspect(player, pointedAt(player));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (enabled(event.getPlayer()) && event.getHand() == EquipmentSlot.HAND && event.getAction().isRightClick()) {
            event.setCancelled(true);
            inspect(event.getPlayer(), pointedAt(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntity(PlayerInteractEntityEvent event) {
        if (enabled(event.getPlayer()) && event.getHand() == EquipmentSlot.HAND) {
            event.setCancelled(true);
            inspect(event.getPlayer(), actor(event.getRightClicked()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityAt(PlayerInteractAtEntityEvent event) {
        onEntity(event);
    }

    private Optional<LivingEntity> actor(Entity clicked) {
        if (clicked instanceof LivingEntity living && game.get().bots().aim(living).isPresent()) {
            return Optional.of(living);
        }
        return game.get().rigs().flatMap(appearance -> appearance.actor(clicked)).filter(body -> game.get().bots().aim(body).isPresent());
    }

    private Optional<LivingEntity> pointedAt(Player player) {
        var eye = player.getEyeLocation();
        var hit = player.getWorld().rayTrace(eye, eye.getDirection(), REACH, FluidCollisionMode.NEVER,
                true, 0.15, entity -> actor(entity).isPresent());
        return hit == null || hit.getHitEntity() == null ? Optional.empty() : actor(hit.getHitEntity());
    }

    private void inspect(Player player, Optional<LivingEntity> selected) {
        if (selected.isEmpty()) {
            player.sendMessage(messages.get("debug.no-bot"));
            return;
        }
        var body = selected.get();
        if (!body.getWorld().equals(player.getWorld()) || player.getEyeLocation().distanceSquared(body.getEyeLocation()) > REACH * REACH
                || !player.hasLineOfSight(body)) {
            player.sendMessage(messages.get("debug.no-bot"));
            return;
        }
        if (requests.containsKey(body.getUniqueId())) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        if (requests.size() >= 3 || tick < cooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(messages.get("debug.busy"));
            return;
        }
        var trace = traces.computeIfAbsent(body.getUniqueId(), ignored -> new BotTrace<>(historyTicks, captureTicks));
        trace.sample(tick, frame(body));
        trace.start(tick);
        var at = player.getLocation();
        String file = "npc-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".json";
        requests.put(body.getUniqueId(), new Request(player.getUniqueId(), body.getUniqueId(), file, Instant.now().toString(),
                tick, Point.of(at), at.getYaw(), at.getPitch(),
                body.getWorld().getKey().asString(), Bukkit.getVersion(), plugin.getPluginMeta().getVersion(),
                System.getProperty("java.version"), plugin.getConfig().saveToString()));
        cooldowns.put(player.getUniqueId(), tick + cooldownTicks);
        player.sendMessage(messages.get("debug.started", Formatter.number("number", game.get().bots().snapshot(body).orElseThrow().number()),
                Formatter.number("seconds", (captureTicks + 19) / 20)));
    }

    private void tick() {
        observers.removeIf(id -> {
            var player = Bukkit.getPlayer(id);
            return player == null || !permitted(player);
        });
        if (observers.isEmpty() && requests.isEmpty()) {
            traces.clear();
            return;
        }
        long tick = Bukkit.getCurrentTick();
        var present = new HashSet<UUID>();
        for (var body : game.get().bots().entities()) {
            present.add(body.getUniqueId());
            var trace = traces.computeIfAbsent(body.getUniqueId(), ignored -> new BotTrace<>(historyTicks, captureTicks));
            trace.sample(tick, frame(body));
        }
        for (var id : List.copyOf(requests.keySet())) {
            traces.get(id).finish(tick, !present.contains(id)).ifPresent(frames -> save(requests.remove(id),
                    present.contains(id) ? "completed" : "bot-removed", frames, false));
        }
        traces.keySet().removeIf(id -> !present.contains(id));
    }

    private Frame frame(LivingEntity body) {
        return new Frame(Bukkit.getCurrentTick(), game.get().tick(), System.nanoTime(), Bukkit.getAverageTickTime(),
                game.get().bots().snapshot(body).orElseThrow(), game.get().rigs().flatMap(appearance -> appearance.snapshot(body)).orElse(null),
                blocks(body), body.getWorld().getNearbyLivingEntities(body.getLocation(), 3).stream()
                .filter(entity -> !entity.equals(body)).limit(12).map(entity -> new Nearby(entity.getUniqueId(),
                        entity.getType().name(), Point.of(entity.getLocation()), Point.of(entity.getVelocity()), entity.isCollidable())).toList());
    }

    private static List<Block> blocks(LivingEntity actor) {
        var at = actor.getLocation();
        var result = new ArrayList<Block>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    var block = at.getWorld().getBlockAt(at.getBlockX() + x, at.getBlockY() + y, at.getBlockZ() + z);
                    if (!block.getType().isAir()) {
                        result.add(new Block(block.getX(), block.getY(), block.getZ(), block.getBlockData().getAsString()));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private void save(Request request, String reason, List<Frame> frames, boolean synchronous) {
        var report = new Report(2, request, Instant.now().toString(), reason, frames);
        var file = plugin.getDataFolder().toPath().resolve("debug").resolve(request.file());
        if (synchronous) {
            completed(request, write(file, report));
        } else {
            Tasks.async(plugin, () -> write(file, report), result -> completed(request, result),
                    () -> message(request.reporter(), "debug.failed", request.file()));
        }
    }

    private static Written write(Path file, Report report) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.toJson(report), StandardOpenOption.CREATE_NEW);
            return new Written(file.toString(), "");
        } catch (IOException error) {
            return new Written(file.toString(), error.toString());
        }
    }

    private void completed(Request request, Written written) {
        if (written.error().isEmpty()) {
            plugin.getSLF4JLogger().info("NPC diagnostic saved: {}", written.file());
            message(request.reporter(), "debug.saved", "Sternenfront/debug/" + request.file());
        } else {
            plugin.getSLF4JLogger().warn("NPC diagnostic write failed: {}", written.error());
            message(request.reporter(), "debug.failed", request.file());
        }
    }

    private void message(UUID recipient, String key, String file) {
        var player = Bukkit.getPlayer(recipient);
        if (player != null) {
            player.sendMessage(messages.get(key, Placeholder.unparsed("file", file)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        observers.remove(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        task.cancel();
        HandlerList.unregisterAll(this);
        for (var entry : requests.entrySet()) {
            traces.get(entry.getKey()).finish(Bukkit.getCurrentTick(), true)
                    .ifPresent(frames -> save(entry.getValue(), "plugin-disabled", frames, true));
        }
        requests.clear();
        observers.clear();
        traces.clear();
        cooldowns.clear();
    }
}
