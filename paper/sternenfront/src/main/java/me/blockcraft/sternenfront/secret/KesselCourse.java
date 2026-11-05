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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Kessel-Lauf as it runs: every deployed player near the course has a {@link KesselRun}, moved once a tick. A
 * running player sees the clock in the action bar; a finish under par finds the secret. Every finish is offered to
 * the player's best time in the table, and the board at the start shows the five fastest players ever.
 */
final class KesselCourse implements Listener {

    private static final int BOARD_SIZE = 5;
    private static final double NEAR = 3;

    private final Plugin plugin;
    private final SternenfrontGame game;
    private final Messages messages;
    private final Discoveries discoveries;
    private final KesselTimes times;
    private final SecretSpots.Course course;
    private final long parTicks;
    private final long limitTicks;
    private final Map<UUID, KesselRun> runs = new HashMap<>();
    private final Map<UUID, Vector> last = new HashMap<>();
    private final TextDisplay board;
    private boolean closed;
    private BestTimes best = new BestTimes(List.of(), BOARD_SIZE);

    KesselCourse(Plugin plugin, SternenfrontGame game, Messages messages, Discoveries discoveries, KesselTimes times,
                 SecretSpots.Course course, SecretsConfig config) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.discoveries = discoveries;
        this.times = times;
        this.course = course;
        this.parTicks = config.parSeconds() * 20L;
        this.limitTicks = config.limitSeconds() * 20L;
        var at = course.board();
        at.getChunk().addPluginChunkTicket(plugin);
        this.board = at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setBrightness(Displays.LIT);
            display.setLineWidth(220);
        });
        draw();
        Tasks.async(plugin, () -> times.best(BOARD_SIZE), loaded -> {
            best = new BestTimes(loaded, BOARD_SIZE);
            draw();
        });
    }

    void tick(long tick) {
        var lane = course.lane();
        var near = lane.clone().expand(NEAR);
        for (var player : course.board().getWorld().getPlayers()) {
            var uuid = player.getUniqueId();
            var at = player.getLocation().toVector();
            var run = runs.get(uuid);
            if (!game.onField(player) || (!near.contains(at) && (run == null || !run.running()))) {
                last.remove(uuid);
                runs.remove(uuid);
                continue;
            }
            var from = last.put(uuid, at);
            if (from == null) {
                continue;
            }
            if (run == null) {
                run = new KesselRun(course.gates(), limitTicks);
                runs.put(uuid, run);
            }
            boolean onCourse = lane.contains(at) && at.getY() >= course.pitY();
            step(player, run, run.move(from.getX(), at.getX(), onCourse, tick), tick);
        }
    }

    private void step(Player player, KesselRun run, KesselRun.Step step, long tick) {
        switch (step) {
            case STARTED, CHECKPOINT -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.8f,
                    step == KesselRun.Step.STARTED ? 1f : 1.4f);
            case FAILED -> player.sendMessage(messages.get("secret.kessel-failed"));
            case FINISHED -> finish(player, run.result());
            case NONE -> { }
        }
        if (run.running() && tick % 2 == 0) {
            BlockcraftApi.get().notifications().status(player, messages.get("secret.kessel-running",
                    Placeholder.unparsed("time", seconds(run.elapsed(tick))), Formatter.number("gate", run.passed()),
                    Formatter.number("gates", run.gates())));
        }
    }

    private void finish(Player player, long ticks) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        player.sendMessage(messages.get("secret.kessel-finished", Placeholder.unparsed("time", seconds(ticks))));
        if (ticks < parTicks) {
            discoveries.find(player, Secret.KESSEL);
        } else {
            player.sendMessage(messages.get("secret.kessel-par", Placeholder.unparsed("par", seconds(parTicks))));
        }
        var uuid = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        best = best.with(new BestTimes.Entry(uuid, name, ticks));
        draw();
        Tasks.async(plugin, () -> times.record(uuid, name, ticks));
    }

    private void draw() {
        if (closed) {
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(messages.get("secret.kessel-board-title", Placeholder.unparsed("par", seconds(parTicks))));
        if (best.entries().isEmpty()) {
            lines.add(messages.get("secret.kessel-board-empty"));
        }
        for (int i = 0; i < best.entries().size(); i++) {
            var entry = best.entries().get(i);
            lines.add(messages.get("secret.kessel-board-line", Formatter.number("place", i + 1),
                    Placeholder.unparsed("name", entry.name()), Placeholder.unparsed("time", seconds(entry.ticks()))));
        }
        board.text(Component.join(JoinConfiguration.newlines(), lines));
    }

    private static String seconds(long ticks) {
        return String.format(Locale.GERMANY, "%.2f", ticks / 20.0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
        last.remove(event.getPlayer().getUniqueId());
    }

    void close() {
        closed = true;
        runs.clear();
        last.clear();
        board.remove();
        course.board().getChunk().removePluginChunkTicket(plugin);
    }
}
