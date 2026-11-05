package me.blockcraft.sternenfront.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.animation.AnimationClip;
import me.blockcraft.api.animation.BedrockAnimations;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.presentation.PresentationScope;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class BattleIntro implements AutoCloseable {
    private final SternenfrontGame game;
    private final Scenes scenes;
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
            running = false;
            ready.run();
        }, duration);
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
        var path = path(faction, duration);
        if (path.isEmpty()) {
            player.sendMessage(game.messages().get("intro.invalid"));
            return;
        }
        var route = path.get();
        var actorAt = route.actorAt(game.map().headquarters().get(faction).spawns().getFirst());
        var config = game.plugin().getConfig();
        var focus = new FocusWindow(config.getBoolean("intro.focus.enabled"),
                config.getDouble("intro.focus.from"), config.getDouble("intro.focus.until"));
        var participant = new Participant(new PresentationScope(game.plugin()));
        var timeline = new SceneTimeline(game.map().world(), route.camera(), List.of(), skipMode,
                (viewer, tick, total, skipped) -> {
                    if (skipped) {
                        if (tick % 10 == 0) viewer.sendActionBar(game.messages().get("intro.waiting",
                                Formatter.number("seconds", Math.max(0, (total - tick + 19) / 20))));
                        return;
                    }
                    if (!participant.initialized) {
                        participant.initialized = true;
                        participant.resources.own(viewer::clearTitle);
                        participant.resources.own(() -> viewer.sendActionBar(Component.empty()));
                    }
                    if (participant.actor != null) participant.actor.tick(route.briefingTick(tick));
                    int fadeTicks = Math.clamp(game.plugin().getConfig().getInt("intro.fade-ticks"), 1, total / 2);
                    double fade = Math.max(1.0 - (double) tick / fadeTicks, 1.0 - (double) (total - tick) / fadeTicks);
                    ScreenEffects.frame(participant.resources, viewer, Math.clamp(fade, 0, 1), true);
                    if (focus.active(tick, total, skipped)) {
                        if (!participant.focused) ScreenEffects.atmosphere(participant.resources, viewer, ScreenEffects.Atmosphere.FOCUS);
                        participant.focused = true;
                    } else if (participant.focused) {
                        ScreenEffects.clearAtmosphere(viewer);
                        participant.focused = false;
                    }
                    if (tick % 10 == 0) {
                        viewer.sendActionBar(game.messages().get("intro.controls",
                                Formatter.number("seconds", Math.max(0, (total - tick + 19) / 20))));
                    }
                    if (tick == fadeTicks) {
                        viewer.showTitle(Title.title(game.messages().get("intro.title", Placeholder.unparsed("map", game.map().name())),
                                game.messages().get("intro.subtitle", game.factionName(faction)),
                                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(500))));
                    }
                });
        boolean played = scenes.play(player, timeline, participant.resources,
                ignored -> player.sendActionBar(Component.empty()));
        if (played && clip.isPresent()) {
            try {
                participant.actor = new CinematicActor(game.plugin(), player, actorAt, faction, clip.get());
                participant.resources.own(participant.actor::close);
            } catch (IllegalArgumentException badClip) {
                game.plugin().getSLF4JLogger().warn("Intro actor could not use animation", badClip);
            }
        }
    }

    private static final class Participant {
        final PresentationScope resources;
        CinematicActor actor;
        boolean initialized;
        boolean focused;

        Participant(PresentationScope resources) {
            this.resources = resources;
        }
    }

    record FocusWindow(boolean enabled, double from, double until) {
        boolean active(int tick, int duration, boolean skipped) {
            if (!enabled || skipped || duration <= 0 || !Double.isFinite(from) || !Double.isFinite(until)
                    || from < 0 || until > 1 || from >= until) return false;
            double progress = (double) tick / duration;
            return progress >= from && progress < until;
        }
    }

    record IntroPath(CameraPath camera, int briefingStart) {
        int briefingTick(int tick) {
            return Math.max(0, tick - briefingStart);
        }

        Location actorAt(Location spawn) {
            var at = spawn.clone();
            var view = camera.sample(briefingStart);
            at.setDirection(view.in(spawn.getWorld()).toVector().subtract(spawn.toVector()));
            at.setPitch(0);
            return at;
        }
    }

    private Optional<IntroPath> path(Faction faction, int duration) {
        try {
            return Optional.of(path(game.plugin().getConfig().getMapList("intro.shots"), duration, name -> anchor(name, faction)));
        } catch (RuntimeException invalid) {
            game.plugin().getSLF4JLogger().warn("Invalid intro camera path", invalid);
            return Optional.empty();
        }
    }

    static IntroPath path(List<Map<?, ?>> shots, int duration, Function<String, Location> anchor) {
        var frames = new ArrayList<CameraPath.Keyframe>();
        int briefingStart = -1;
        for (var shot : shots) {
            int tick = (int) Math.round(((Number) shot.get("at")).doubleValue() * duration);
            var name = String.valueOf(shot.get("anchor"));
            var at = anchor.apply(name).clone();
            var offset = (List<?>) shot.get("offset");
            at.add(((Number) offset.get(0)).doubleValue(), ((Number) offset.get(1)).doubleValue(), ((Number) offset.get(2)).doubleValue());
            var target = anchor.apply(String.valueOf(shot.get("look-at"))).clone().add(0, 1.4, 0);
            at.setDirection(target.toVector().subtract(at.toVector()));
            frames.add(new CameraPath.Keyframe(tick, CameraPath.Pose.of(at), Boolean.TRUE.equals(shot.get("cut"))));
            if (briefingStart < 0 && name.equals("headquarters")) briefingStart = tick;
        }
        var path = new CameraPath(frames);
        if (path.duration() != duration) throw new IllegalArgumentException("Intro must end at fraction 1");
        return new IntroPath(path, Math.max(0, briefingStart));
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
    }

    @Override
    public void close() {
        clear();
        scenes.close();
    }
}
