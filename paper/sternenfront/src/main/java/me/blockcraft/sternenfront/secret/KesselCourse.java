package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.arena.SecretSpots;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class KesselCourse implements Listener {

    private static final int BOARD_SIZE = 5;
    private static final double NEAR = 3;

    private final Plugin plugin;
    private final SternenfrontGame game;
    private final Messages messages;
    private final Discoveries discoveries;
    private final Optional<KesselTimes> times;
    private final Secret secret;
    private final String messagePrefix;
    private final SecretSpots.Course course;
    private final long parTicks;
    private final long limitTicks;
    private final Map<UUID, CourseAttempt> runs = new HashMap<>();
    private final TextDisplay board;
    private boolean closed;
    private BestTimes best = new BestTimes(List.of(), BOARD_SIZE);

    KesselCourse(Plugin plugin, SternenfrontGame game, Messages messages, Discoveries discoveries, Optional<KesselTimes> times,
                 Secret secret, SecretSpots.Course course, int parSeconds, int limitSeconds) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.discoveries = discoveries;
        this.times = times;
        this.secret = secret;
        this.messagePrefix = secret == Secret.INSPECTION ? "secret.inspection-" : "secret.kessel-";
        this.course = course;
        this.parTicks = parSeconds * 20L;
        this.limitTicks = limitSeconds * 20L;
        var at = course.board();
        at.getChunk().addPluginChunkTicket(plugin);
        this.board = at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setBrightness(Displays.LIT);
            display.setLineWidth(220);
        });
        draw();
        times.ifPresent(store -> Tasks.async(plugin, () -> store.best(BOARD_SIZE), loaded -> {
            best = new BestTimes(loaded, BOARD_SIZE);
            draw();
        }));
    }

    void tick(long tick) {
        var lane = course.lane();
        var near = lane.clone().expand(NEAR);
        for (var player : course.board().getWorld().getPlayers()) {
            var uuid = player.getUniqueId();
            var at = player.getLocation().toVector();
            var attempt = runs.get(uuid);
            boolean eligible = game.onField(player) && !player.isInsideVehicle();
            if ((!eligible && attempt == null) || (!near.contains(at) && (attempt == null || !attempt.run().running()))) {
                runs.remove(uuid);
                continue;
            }
            if (attempt == null) {
                attempt = new CourseAttempt(course, limitTicks);
                runs.put(uuid, attempt);
            }
            step(player, attempt.run(), attempt.move(at, eligible, tick), tick);
            if (!eligible) {
                runs.remove(uuid);
            }
        }
    }

    private void step(Player player, KesselRun run, KesselRun.Step step, long tick) {
        switch (step) {
            case STARTED, CHECKPOINT -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.8f,
                    step == KesselRun.Step.STARTED ? 1f : 1.4f);
            case FAILED -> player.sendMessage(messages.get(messagePrefix + "failed"));
            case FINISHED -> finish(player, run.result());
            case NONE -> { }
        }
        if (run.running() && tick % 2 == 0) {
            BlockcraftApi.get().notifications().status(player, messages.get(messagePrefix + "running",
                    Placeholder.unparsed("time", seconds(run.elapsed(tick))), Formatter.number("gate", run.passed()),
                    Formatter.number("gates", run.gates())));
        }
    }

    private void finish(Player player, long ticks) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        player.sendMessage(messages.get(messagePrefix + "finished", Placeholder.unparsed("time", seconds(ticks))));
        if (secret == Secret.INSPECTION || ticks < parTicks) {
            discoveries.find(player, secret);
        }
        if (ticks >= parTicks) {
            player.sendMessage(messages.get(messagePrefix + "par", Placeholder.unparsed("par", seconds(parTicks))));
        }
        var uuid = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        best = best.with(new BestTimes.Entry(uuid, name, ticks));
        draw();
        times.ifPresent(store -> Tasks.async(plugin, () -> store.record(uuid, name, ticks)));
    }

    private void draw() {
        if (closed) {
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(messages.get(messagePrefix + "board-title", Placeholder.unparsed("par", seconds(parTicks)),
                Placeholder.unparsed("limit", seconds(limitTicks))));
        if (best.entries().isEmpty()) {
            lines.add(messages.get(messagePrefix + "board-empty"));
        }
        for (int i = 0; i < best.entries().size(); i++) {
            var entry = best.entries().get(i);
            lines.add(messages.get(messagePrefix + "board-line", Formatter.number("place", i + 1),
                    Placeholder.unparsed("name", entry.name()), Placeholder.unparsed("time", seconds(entry.ticks()))));
        }
        board.text(Component.join(JoinConfiguration.newlines(), lines));
    }

    private static String seconds(long ticks) {
        return String.format(Locale.GERMANY, "%.2f", ticks / 20.0);
    }

    private void interrupt(Player player) {
        var attempt = runs.remove(player.getUniqueId());
        if (attempt != null && attempt.interrupt() == KesselRun.Step.FAILED) {
            player.sendMessage(messages.get(messagePrefix + "failed"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        interrupt(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            interrupt(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
    }

    void close() {
        closed = true;
        runs.clear();
        board.remove();
        course.board().getChunk().removePluginChunkTicket(plugin);
    }
}
