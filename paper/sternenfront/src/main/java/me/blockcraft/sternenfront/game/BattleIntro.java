package me.blockcraft.sternenfront.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.animation.AnimationClip;
import me.blockcraft.api.animation.BedrockAnimations;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.scene.CameraPath;
import me.blockcraft.api.scene.SceneTimeline;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.sternenfront.appearance.CinematicActor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BattleIntro implements AutoCloseable {
    private final SternenfrontGame game;
    private final Scenes scenes;
    private final Map<UUID, CinematicActor> actors = new HashMap<>();
    private final Optional<AnimationClip> clip;
    private BukkitTask deadline;
    private boolean running;

    BattleIntro(SternenfrontGame game) {
        this.game = game;
        scenes = new Scenes(game.plugin());
        clip = loadClip();
    }

    public boolean running() {
        return running;
    }

    public void start(List<Player> players, Runnable ready) {
        if (!game.plugin().getConfig().getBoolean("intro.enabled")) {
            ready.run();
            return;
        }
        running = true;
        int duration = duration();
        deadline = game.plugin().getServer().getScheduler().runTaskLater(game.plugin(), () -> {
            deadline = null;
            for (var player : game.plugin().getServer().getOnlinePlayers()) scenes.stop(player);
            actors.values().forEach(CinematicActor::close);
            actors.clear();
            running = false;
            ready.run();
        }, duration + 1L);
        for (var player : players) {
            try {
                play(player, duration, SceneTimeline.SkipMode.HOLD);
            } catch (RuntimeException failure) {
                game.plugin().getSLF4JLogger().warn("Could not prepare intro participant", failure);
                scenes.stop(player);
            }
        }
    }

    public void cancel(Player player) {
        scenes.stop(player);
    }

    private int duration() {
        return Math.clamp(game.plugin().getConfig().getInt("intro.duration-ticks"), 40, 600);
    }

    private void play(Player player, int duration, SceneTimeline.SkipMode skipMode) {
        if (!BlockcraftApi.get().resourcePack().applied(player)) {
            player.sendMessage(game.messages().get("intro.pack-required"));
            return;
        }
        var faction = game.factionOrSmallest(player);
        var actorAt = game.map().headquarters().get(faction).spawns().getFirst().clone();
        var path = path(faction, duration);
        if (path.isEmpty()) {
            player.sendMessage(game.messages().get("intro.invalid"));
            return;
        }
        var timeline = new SceneTimeline(game.map().world(), path.get(), List.of(), skipMode,
                (viewer, tick, total, skipped) -> {
                    var actor = actors.get(viewer.getUniqueId());
                    if (actor != null) actor.tick(tick);
                    int fadeTicks = Math.clamp(game.plugin().getConfig().getInt("intro.fade-ticks"), 1, total / 2);
                    double fade = Math.max(1.0 - (double) tick / fadeTicks, 1.0 - (double) (total - tick) / fadeTicks);
                    ScreenEffects.frame(viewer, Math.clamp(fade, 0, 1), true);
                    if (tick % 10 == 0) {
                        viewer.sendActionBar(game.messages().get(skipped ? "intro.waiting" : "intro.controls",
                                Formatter.number("seconds", Math.max(0, (total - tick + 19) / 20))));
                    }
                    if (tick == fadeTicks) {
                        viewer.showTitle(Title.title(game.messages().get("intro.title", Placeholder.unparsed("map", game.map().name())),
                                game.messages().get("intro.subtitle", game.factionName(faction)),
                                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(500))));
                    }
                });
        boolean played = scenes.play(player, timeline, ignored -> {
            var actor = actors.remove(player.getUniqueId());
            if (actor != null) actor.close();
            ScreenEffects.clear(player);
            player.clearTitle();
            player.sendActionBar(Component.empty());
        });
        if (played && clip.isPresent()) {
            try {
                actors.put(player.getUniqueId(), new CinematicActor(game.plugin(), player, actorAt, faction, clip.get()));
            } catch (IllegalArgumentException badClip) {
                game.plugin().getSLF4JLogger().warn("Intro actor could not use animation", badClip);
            }
        }
    }

    private Optional<CameraPath> path(Faction faction, int duration) {
        try {
            var frames = new ArrayList<CameraPath.Keyframe>();
            for (var shot : game.plugin().getConfig().getMapList("intro.shots")) {
                int tick = (int) Math.round(((Number) shot.get("at")).doubleValue() * duration);
                var at = anchor(String.valueOf(shot.get("anchor")), faction);
                var offset = (List<?>) shot.get("offset");
                at.add(((Number) offset.get(0)).doubleValue(), ((Number) offset.get(1)).doubleValue(), ((Number) offset.get(2)).doubleValue());
                var target = anchor(String.valueOf(shot.get("look-at")), faction).add(0, 1.4, 0);
                at.setDirection(target.toVector().subtract(at.toVector()));
                frames.add(new CameraPath.Keyframe(tick, CameraPath.Pose.of(at), Boolean.TRUE.equals(shot.get("cut"))));
            }
            var path = new CameraPath(frames);
            if (path.duration() != duration) throw new IllegalArgumentException("Intro must end at fraction 1");
            return Optional.of(path);
        } catch (RuntimeException invalid) {
            game.plugin().getSLF4JLogger().warn("Invalid intro camera path", invalid);
            return Optional.empty();
        }
    }

    private Location anchor(String name, Faction faction) {
        if (name.equals("overview")) return game.map().overview().clone();
        if (name.equals("headquarters")) return game.map().headquarters().get(faction).spawns().getFirst().clone();
        if (name.startsWith("post:")) return game.map().posts().get(Integer.parseInt(name.substring(5))).center().clone();
        throw new IllegalArgumentException("Unknown camera anchor: " + name);
    }

    private Optional<AnimationClip> loadClip() {
        var override = game.plugin().getDataFolder().toPath().resolve("animations/briefing.animation.json");
        try (var input = Files.isRegularFile(override) ? Files.newInputStream(override)
                : game.plugin().getResource("animations/briefing.animation.json")) {
            if (input == null) return Optional.empty();
            return switch (BedrockAnimations.read(new InputStreamReader(input, StandardCharsets.UTF_8), "animation.infantry.briefing")) {
                case BedrockAnimations.Result.Loaded loaded -> Optional.of(loaded.clip());
                case BedrockAnimations.Result.Invalid invalid -> {
                    game.plugin().getSLF4JLogger().warn("Intro animation rejected: {}", invalid.reason());
                    yield Optional.empty();
                }
            };
        } catch (IOException failure) {
            game.plugin().getSLF4JLogger().warn("Cannot load intro animation", failure);
            return Optional.empty();
        }
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("sfintro")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("blockcraft.sternenfront.presentation"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (game.phase() != GamePhase.LOBBY || Scenes.isViewing(player)) {
                        player.sendMessage(game.messages().get("intro.lobby-only"));
                    } else {
                        play(player, duration(), SceneTimeline.SkipMode.COMPLETE);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("stop").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (!running) cancel(player);
                    else scenes.skip(player);
                    return Command.SINGLE_SUCCESS;
                })).build();
    }

    public void clear() {
        if (deadline != null) {
            deadline.cancel();
            deadline = null;
        }
        running = false;
        for (var player : game.plugin().getServer().getOnlinePlayers()) scenes.stop(player);
        actors.values().forEach(CinematicActor::close);
        actors.clear();
    }

    @Override
    public void close() {
        clear();
        scenes.close();
    }
}
