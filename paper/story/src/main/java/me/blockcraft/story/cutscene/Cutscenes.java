package me.blockcraft.story.cutscene;

import kr.toxicity.model.api.tracker.EntityTracker;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.scene.CameraPath;
import me.blockcraft.api.scene.SceneTimeline;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.common.message.Messages;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.DialoguePanel;
import me.blockcraft.story.dialogue.Voices;
import me.blockcraft.story.game.Bodies;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Plays a {@link Cutscene} to the whole party at once. Each member gets their own camera, subtitles, voices and sounds;
 * the cast, the craft and the effects are shared, so they follow whichever member's camera has come furthest, and the
 * story goes on once every member's scene has ended. An actor named like one of the mission's NPCs plays in that NPC's
 * own figure; every other actor is spawned for the scene. The cast wears BetterModel bodies when BetterModel runs;
 * without it the NPCs stay plain figures and the rest of the cast stays away.
 * <p>
 * A scene every viewer has watched before may be skipped: holding sneak asks for it, and it ends for all of them once
 * they all hold it for {@link #SKIP_HOLD_TICKS}. A scene someone has not seen yet plays to its end.
 */
public final class Cutscenes implements Listener, AutoCloseable {

    /** A mission NPC who plays in their own figure when a scene's cast names them. */
    public record Npc(LivingEntity body, String model) { }

    private static final int RISE = 6;
    /**
     * At a far cut the screen stays black at least {@link #DIP_HOLD_TICKS} and until the chunks within
     * {@link #ARRIVED_CHUNK_RADIUS} of the new camera have reached the viewer, but no longer than
     * {@link #DIP_WAIT_TICKS}, so a slow connection shows a world that is still loading rather than holding the scene.
     */
    private static final int DIP_HOLD_TICKS = 4;
    private static final int DIP_WAIT_TICKS = 40;
    private static final int ARRIVED_CHUNK_RADIUS = 2;
    /** How many chunks around a far cut's camera stay loaded for the scene, so they are ready to send when it cuts. */
    private static final int KEPT_CHUNK_RADIUS = 4;
    /** How many chunks around the camera's path, sampled every {@link #PATH_STEP_TICKS}, load before the scene starts. */
    private static final int PATH_CHUNK_RADIUS = 3;
    private static final int PATH_STEP_TICKS = 10;
    private static final int READY_CHECK_TICKS = 5;
    private static final int SKIP_HOLD_TICKS = 10;
    /** How long a skippable scene says so at its start, before only a held sneak brings the hint back. */
    private static final int SKIP_HINT_TICKS = 80;
    /** A display's moves glide over this many ticks, the longest a teleport may glide being 59. */
    private static final int CRAFT_STEP = 10;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private final Plugin plugin;
    private final Messages messages;
    private final Voices voices;
    private final Scenes scenes;
    private final SeenScenes seen;
    private final Demolition demolition;
    private final DialogueHud.Typing typing;
    private final Settings settings;
    private final Set<Showing> showing = new HashSet<>();
    private final Set<UUID> holding = new HashSet<>();

    /**
     * {@code fadeTicks} fade a scene in and out, and out and in again around a cut further than {@code farCutBlocks},
     * whose new place the client still has to load; {@code paces} are how far each body's walk carries it per tick, by
     * the last part of its model's name.
     */
    public record Settings(int fadeTicks, double farCutBlocks, int readyTimeoutTicks, Map<String, Double> paces) {
        public static Settings from(ConfigurationSection section) {
            var paces = section.getConfigurationSection("walk-paces");
            return new Settings(Math.max(1, section.getInt("fade-ticks")), section.getDouble("far-cut-blocks"),
                    section.getInt("ready-timeout-seconds") * 20,
                    paces == null ? Map.of() : paces.getKeys(false).stream().collect(Collectors.toUnmodifiableMap(
                            key -> key, paces::getDouble)));
        }

        double pace(String model) {
            return paces.getOrDefault(model.substring(model.lastIndexOf('_') + 1), 0.11);
        }
    }

    public Cutscenes(Plugin plugin, Messages messages, Voices voices, SeenScenes seen, DialogueHud.Typing typing, Settings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.voices = voices;
        this.seen = seen;
        this.scenes = new Scenes(plugin, false);
        this.demolition = new Demolition(plugin);
        this.typing = typing;
        this.settings = settings;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Where the scene that is playing has got to. */
    public record Progress(String scene, int tick, int duration, int viewers) { }

    /**
     * Plays {@code cutscene}, handing each of its package actions to {@code run} as the scene reaches it. The camera
     * starts once every viewer's resource pack has loaded and the chunks along its path are in memory, or after
     * {@code ready-timeout-seconds}: a client still loading its pack or a server loading the world mid-shot would see the
     * scene run on in the dark and then jump ahead.
     */
    public void play(Cutscene cutscene, List<Player> viewers, boolean party, Map<String, Npc> npcs, Consumer<String> run, Runnable done) {
        boolean skippable = !viewers.isEmpty() && viewers.stream().allMatch(viewer -> seen.seen(viewer, cutscene.name()));
        var show = new Showing(cutscene, party, npcs, viewers, skippable, run, done);
        var farCuts = cutscene.farCuts(settings.farCutBlocks());
        showing.add(show);
        if (viewers.isEmpty()) {
            show.end();
            return;
        }
        var loaded = show.keepLoaded(viewers.getFirst().getWorld(), farCuts);
        var hint = messages.get("cutscene.loading");
        var waited = new int[1];
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (show.finished) {
                task.cancel();
                return;
            }
            var pack = BlockcraftApi.get().resourcePack();
            var here = viewers.stream().filter(Player::isOnline).toList();
            boolean ready = loaded.isDone() && here.stream().allMatch(pack::applied);
            if (!ready && waited[0] < settings.readyTimeoutTicks()) {
                here.forEach(viewer -> viewer.sendActionBar(hint));
                waited[0] += READY_CHECK_TICKS;
                return;
            }
            task.cancel();
            if (waited[0] > 0) {
                plugin.getSLF4JLogger().info("[story] scene {} waited {} ticks for {}", cutscene.name(), waited[0],
                        loaded.isDone() ? "resource packs" : "chunks");
            }
            viewers.stream().filter(viewer -> !viewer.isOnline()).forEach(show::ended);
            start(show, cutscene, here, farCuts);
        }, 0, READY_CHECK_TICKS);
    }

    private void start(Showing show, Cutscene cutscene, List<Player> viewers, List<Integer> farCuts) {
        show.started = true;
        // The scene's first frame is as far from where the viewer stood as a far cut, so it stays dark until it has loaded.
        var dips = new ArrayList<Integer>(farCuts);
        dips.addFirst(0);
        for (var viewer : viewers) {
            var scope = new PresentationScope(plugin);
            var heard = new int[] {-1};
            var hinted = new boolean[1];
            var shown = new HashMap<Integer, Integer>();
            scope.own(() -> {
                viewer.sendActionBar(Component.empty());
                viewer.stopSound(SoundStop.source(Sound.Source.VOICE));
                viewer.stopSound(SoundStop.source(Sound.Source.AMBIENT));
            });
            var timeline = new SceneTimeline(viewer.getWorld(), cutscene.camera(), List.of(), SceneTimeline.SkipMode.COMPLETE,
                    (player, tick, total, skipped) -> {
                        ScreenEffects.frame(scope, player, darkness(cutscene, player, tick, total, dips, shown), true);
                        show.reach(tick);
                        while (heard[0] < tick) hear(cutscene, player, ++heard[0]);
                        hinted[0] = subtitle(cutscene, player, tick, show.skipHint(tick), hinted[0]);
                    });
            boolean started = scenes.play(viewer, timeline, scope, end -> {
                // A preview may start mid-scene and a forced skip shows nothing, so only the party's own viewing counts.
                if (show.party && !show.forced && (end == Scenes.End.COMPLETED || end == Scenes.End.SKIPPED)) {
                    seen.record(viewer, cutscene.name());
                }
                show.ended(viewer);
            });
            if (!started) show.ended(viewer);
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        if (event.getInput().isSneak()) {
            holding.add(event.getPlayer().getUniqueId());
        } else {
            holding.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        holding.remove(event.getPlayer().getUniqueId());
    }

    public boolean playing(Player player) {
        return scenes.active(player);
    }

    public void stop(Player player) {
        scenes.stop(player);
    }

    /** Whether the party's own scene plays, as opposed to a preview. */
    public boolean partyPlaying() {
        return showing.stream().anyMatch(show -> show.party);
    }

    public Optional<Progress> progress() {
        return showing.stream().findFirst().map(show -> new Progress(show.cutscene.name(), Math.max(0, show.reached),
                show.cutscene.duration(), show.watching.size()));
    }

    /** Ends every scene for everyone at once, seen before or not, and goes on with the story. */
    public void skipAll() {
        for (var show : List.copyOf(showing)) {
            show.forced = true;
            show.skipping = true;
            if (show.started) {
                List.copyOf(show.watching).forEach(scenes::skip);
            } else {
                show.end();
            }
        }
    }

    /** Ends every scene without going on with the story, as a jump does. */
    public void cancelAll() {
        List.copyOf(showing).forEach(this::cancel);
    }

    /** Ends the previews and leaves the party's scene playing; a party scene ends with a skip, so the story goes on. */
    public void cancelPreviews() {
        List.copyOf(showing).stream().filter(show -> !show.party).forEach(this::cancel);
    }

    private void cancel(Showing show) {
        show.cancelled = true;
        List.copyOf(show.watching).forEach(scenes::stop);
        show.end();
    }

    /**
     * How dark the picture is: fading in at the start, out at the end, and out and back in around each far cut, where
     * {@code shown} keeps the tick at which the viewer's new place was ready.
     */
    private double darkness(Cutscene cutscene, Player viewer, int tick, int total, List<Integer> farCuts, Map<Integer, Integer> shown) {
        int fade = settings.fadeTicks();
        double dark = Math.max(1.0 - (double) tick / fade, 1.0 - (double) (total - tick) / fade);
        for (int cut : farCuts) {
            if (tick < cut) {
                dark = Math.max(dark, 1.0 - (double) (cut - tick - 1) / fade);
                continue;
            }
            int since = tick - cut;
            if (!shown.containsKey(cut) && since >= DIP_HOLD_TICKS && (since >= DIP_WAIT_TICKS || arrived(viewer, cutscene.camera().sample(cut)))) {
                shown.put(cut, tick);
            }
            dark = Math.max(dark, shown.containsKey(cut) ? 1.0 - (double) (tick - shown.get(cut)) / fade : 1);
        }
        return Math.clamp(dark, 0, 1);
    }

    private static boolean arrived(Player viewer, CameraPath.Pose at) {
        int centreX = (int) Math.floor(at.x()) >> 4;
        int centreZ = (int) Math.floor(at.z()) >> 4;
        for (int x = centreX - ARRIVED_CHUNK_RADIUS; x <= centreX + ARRIVED_CHUNK_RADIUS; x++) {
            for (int z = centreZ - ARRIVED_CHUNK_RADIUS; z <= centreZ + ARRIVED_CHUNK_RADIUS; z++) {
                if (!viewer.isChunkSent(Chunk.getChunkKey(x, z))) return false;
            }
        }
        return true;
    }

    /** How long {@code line} is spoken: its voice's length, or its subtitle's where it has no voice. */
    private int spoken(Cutscene.Line line) {
        return voices.of(line.text()).map(Voices.Voice::ticks).orElse(line.ticks());
    }

    /** The voices and sounds that start at {@code tick}, for this viewer alone, since each may skip on their own. */
    private void hear(Cutscene cutscene, Player viewer, int tick) {
        for (var line : cutscene.lines()) {
            if (line.tick() == tick) {
                voices.of(line.text()).ifPresent(voice -> viewer.playSound(Sound.sound(voice.sound(), Sound.Source.VOICE, 1, 1),
                        Sound.Emitter.self()));
            }
        }
        for (var cue : cutscene.sounds()) {
            if (cue.tick() != tick) continue;
            var sound = Sound.sound(Key.key(cue.sound()), Sound.Source.AMBIENT, cue.volume(), cue.pitch());
            cue.at().ifPresentOrElse(at -> viewer.playSound(sound, at.getX(), at.getY(), at.getZ()),
                    () -> viewer.playSound(sound, Sound.Emitter.self()));
        }
    }

    /**
     * The line in the dialogue panel, typing out as a conversation's does, with {@code hint} in its corner; between
     * lines the hint stands alone. Returns whether a lone hint is showing, so it is cleared once it goes.
     */
    private boolean subtitle(Cutscene cutscene, Player viewer, int tick, String hint, boolean hinted) {
        var spoken = cutscene.lineAt(tick);
        if (spoken.isPresent()) {
            var line = spoken.get();
            var page = DialoguePanel.pages(plain(messages.get(line.text()))).getFirst();
            int age = tick - line.tick();
            viewer.sendActionBar(DialoguePanel.render(new DialoguePanel.View(plain(messages.get("cutscene.speaker." + line.speaker())),
                    DialoguePanel.revealed(page, (int) ((age + 1) * typing.rate(DialoguePanel.length(page)))), false, List.of(), 0, hint,
                    Math.max(0, RISE - age * 2), false), new DialoguePanel.Size(page.size(), 0, !hint.isEmpty())));
            return false;
        }
        if (!hint.isEmpty()) {
            viewer.sendActionBar(Component.text(hint, NamedTextColor.GRAY));
            return true;
        }
        if (hinted || cutscene.lines().stream().anyMatch(line -> line.tick() + line.ticks() == tick)) viewer.sendActionBar(Component.empty());
        return false;
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private final class Showing {
        private final Cutscene cutscene;
        private final boolean party;
        private final Map<String, Npc> npcs;
        private final Set<Player> watching;
        private final boolean skippable;
        private final Consumer<String> run;
        private final Runnable done;
        private final Map<String, LivingEntity> cast = new HashMap<>();
        private final Map<String, EntityTracker> bodies = new HashMap<>();
        private final Map<String, Boolean> walking = new HashMap<>();
        private final Map<String, Double> pace = new HashMap<>();
        private final Map<String, Set<String>> playing = new HashMap<>();
        private final List<Entity> spawned = new ArrayList<>();
        private final Map<Cutscene.Craft, ItemDisplay> flying = new HashMap<>();
        private final List<Chunk> kept = new ArrayList<>();
        private int reached = -1;
        private int held;
        private boolean skipping;
        private boolean finished;
        private boolean cancelled;
        private boolean started;
        private boolean forced;

        Showing(Cutscene cutscene, boolean party, Map<String, Npc> npcs, List<Player> viewers, boolean skippable, Consumer<String> run,
                Runnable done) {
            this.cutscene = cutscene;
            this.party = party;
            this.npcs = npcs;
            this.watching = new HashSet<>(viewers);
            this.skippable = skippable;
            this.run = run;
            this.done = done;
        }

        private long holdingCount() {
            return watching.stream().filter(viewer -> holding.contains(viewer.getUniqueId())).count();
        }

        /** Whether and how far the party is asking to skip, for the corner of the subtitle; empty when it may not. */
        String skipHint(int tick) {
            if (!skippable || skipping) return "";
            long holders = holdingCount();
            if (holders > 0 && watching.size() > 1) {
                return plain(messages.get("cutscene.skip.holding", Formatter.number("holding", holders), Formatter.number("viewers", watching.size())));
            }
            return holders > 0 || tick < SKIP_HINT_TICKS ? plain(messages.get("cutscene.skip.hint")) : "";
        }

        /** Once everyone still watching has held sneak long enough, every scene ends; a step later, outside the camera's own tick. */
        private void considerSkipping() {
            if (!skippable || skipping || watching.isEmpty()) return;
            held = holdingCount() == watching.size() ? held + 1 : 0;
            if (held < SKIP_HOLD_TICKS) return;
            skipping = true;
            Bukkit.getScheduler().runTask(plugin, () -> List.copyOf(watching).forEach(scenes::skip));
        }

        void reach(int tick) {
            while (reached < tick && !finished) {
                reached++;
                act(reached);
            }
        }

        private void act(int tick) {
            if (tick == 0) cast();
            considerSkipping();
            cutscene.actors().forEach((name, actor) -> {
                var entity = cast.get(name);
                if (entity == null || !entity.isValid()) return;
                actor.at(tick).ifPresent(at -> {
                    entity.teleport(at);
                    entity.setRotation(at.getYaw(), at.getPitch());
                });
                var body = bodies.get(name);
                if (body == null) return;
                pace.put(name, actor.speed(tick) / settings.pace(actor.model()));
                boolean moving = actor.walking(tick);
                if (!Boolean.valueOf(moving).equals(walking.put(name, moving))) {
                    if (moving) {
                        Bodies.walk(body, () -> (float) Math.clamp(pace.getOrDefault(name, 1.0), 0.3, 2));
                    } else {
                        Bodies.stopWalking(body);
                    }
                }
                for (var line : cutscene.lines()) {
                    if (!line.speaker().equals(name)) continue;
                    if (line.tick() == tick) {
                        Bodies.talk(body, true);
                        playing.computeIfAbsent(name, ignored -> new HashSet<>()).add("talk");
                    } else if (line.tick() + spoken(line) == tick) {
                        Bodies.talk(body, false);
                    }
                }
                for (var gesture : actor.gestures()) {
                    if (gesture.tick() != tick) continue;
                    if (gesture.stop()) {
                        body.stopAnimation(gesture.animation());
                    } else {
                        Bodies.gesture(body, gesture.animation());
                        playing.computeIfAbsent(name, ignored -> new HashSet<>()).add(gesture.animation());
                    }
                }
            });
            for (var effect : cutscene.effects()) {
                if (effect.tick() == tick) effect(effect);
            }
            for (var craft : cutscene.craft()) fly(craft, tick);
            for (var emitter : cutscene.emitters()) {
                if (tick % emitter.every() == 0) emit(emitter);
            }
            for (var cue : cutscene.runs()) {
                if (cue.tick() == tick) run.accept(cue.action());
            }
        }

        /** An NPC plays in their own body; everyone else is spawned in theirs, and only where BetterModel draws it. */
        private void cast() {
            cutscene.actors().forEach((name, actor) -> {
                var npc = npcs.get(name);
                if (npc != null) {
                    if (npc.body().isValid()) {
                        cast.put(name, npc.body());
                        dress(name, npc.body(), npc.model(), actor);
                    }
                    return;
                }
                if (!Bodies.available() || actor.model().isBlank()) return;
                actor.at(0).ifPresent(at -> {
                    var extra = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
                        mannequin.setPersistent(false);
                        mannequin.setImmovable(true);
                        mannequin.setInvulnerable(true);
                        mannequin.setGravity(false);
                        mannequin.setDescription(null);
                    });
                    spawned.add(extra);
                    cast.put(name, extra);
                    dress(name, extra, actor.model(), actor);
                });
            });
        }

        private void dress(String name, LivingEntity entity, String model, Cutscene.Actor actor) {
            Bodies.dress(entity, model).ifPresent(body -> {
                bodies.put(name, body);
                if (!actor.idle().isBlank()) {
                    Bodies.idle(body, actor.idle());
                    playing.computeIfAbsent(name, ignored -> new HashSet<>()).add(actor.idle());
                }
            });
        }

        private void effect(Cutscene.Effect effect) {
            var at = effect.at();
            switch (effect.type()) {
                case EXPLOSION -> {
                    if (effect.radius() > 0) demolition.blast(at, effect.radius());
                    at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 0, 0, 0, 0, null, true);
                    at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 40, 2, 2, 2, 0.05, null, true);
                    at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 6, 0.7f);
                }
                case FLARE -> {
                    at.getWorld().spawnParticle(Particle.FIREWORK, at, 60, 0.4, 0.4, 0.4, 0.15, null, true);
                    at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 4, 1);
                }
            }
        }

        /** Moves a ship along its run in steps the client glides through, nose towards where it is going. */
        private void fly(Cutscene.Craft craft, int tick) {
            int age = tick - craft.tick();
            if (age < 0 || (craft.stays() && age > craft.ticks())) return;
            var heading = craft.to().toVector().subtract(craft.from().toVector());
            if (age == 0) {
                var start = craft.from().clone().setDirection(heading);
                var ship = start.getWorld().spawn(start, ItemDisplay.class, display -> {
                    display.setPersistent(false);
                    display.setItemStack(Items.of(Material.PAPER).model(Key.key("blockcraft", craft.model())).build());
                    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    // Minecraft's item-display renderer adds a half turn after this transformation.
                    display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(craft.scale()),
                            new Quaternionf().rotationY((float) Math.PI)));
                    display.setBrightness(LIT);
                    display.setViewRange(8);
                    display.setTeleportDuration(CRAFT_STEP);
                });
                spawned.add(ship);
                flying.put(craft, ship);
                return;
            }
            var ship = flying.get(craft);
            if (ship == null || !ship.isValid()) return;
            if (age == craft.ticks() && craft.stays()) return;
            if (age % CRAFT_STEP == 0) {
                // A craft that does not stay flies on past `to` at the same speed until the scene ends, so it leaves the
                // shot rather than vanishing in it.
                double ahead = (double) (age + CRAFT_STEP) / craft.ticks();
                if (craft.stays()) ahead = Math.min(1, ahead);
                ship.teleport(craft.from().clone().add(heading.clone().multiply(ahead)).setDirection(heading));
            }
            if (age == craft.ticks() / 2) {
                var at = ship.getLocation();
                at.getWorld().playSound(Sound.sound(Key.key("blockcraft", "story.craft.pass"),
                        Sound.Source.AMBIENT, 6, 1), at.getX(), at.getY(), at.getZ());
            }
        }

        private void emit(Cutscene.Emitter emitter) {
            var at = emitter.at();
            var world = at.getWorld();
            switch (emitter.type()) {
                case SMOKE -> world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 2, 0.4, 0.2, 0.4, 0.01, null, true);
                case DUST -> world.spawnParticle(Particle.DUST, at, 6, 6, 1.5, 6, 0,
                        new Particle.DustOptions(Color.fromRGB(0xD9BC8A), 1.6f), true);
                case SPARKS -> world.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.3, 0.3, 0.3, 0.2, null, true);
                case EMBERS -> world.spawnParticle(Particle.LAVA, at, 1, 0.6, 0.2, 0.6, 0, null, true);
            }
        }

        /**
         * Loads the chunks along the camera's path, and wider around each far cut, and keeps them until the scene ends.
         * The future completes once they are all in memory.
         */
        CompletableFuture<Void> keepLoaded(World world, List<Integer> cuts) {
            var wanted = new HashSet<Long>();
            for (int tick = 0; tick <= cutscene.duration(); tick += PATH_STEP_TICKS) around(cutscene.camera().sample(tick), PATH_CHUNK_RADIUS, wanted);
            around(cutscene.camera().sample(cutscene.duration()), PATH_CHUNK_RADIUS, wanted);
            for (int cut : cuts) around(cutscene.camera().sample(cut), KEPT_CHUNK_RADIUS, wanted);
            return CompletableFuture.allOf(wanted.stream().map(key -> world.getChunkAtAsync((int) (long) key, (int) (key >> 32)).thenAccept(chunk -> {
                if (!finished && !chunk.getPluginChunkTickets().contains(plugin) && chunk.addPluginChunkTicket(plugin)) {
                    kept.add(chunk);
                }
            })).toArray(CompletableFuture[]::new));
        }

        private static void around(CameraPath.Pose at, int radius, Set<Long> chunks) {
            int centreX = (int) Math.floor(at.x()) >> 4;
            int centreZ = (int) Math.floor(at.z()) >> 4;
            for (int x = centreX - radius; x <= centreX + radius; x++) {
                for (int z = centreZ - radius; z <= centreZ + radius; z++) chunks.add(Chunk.getChunkKey(x, z));
            }
        }

        void ended(Player viewer) {
            watching.remove(viewer);
            if (watching.isEmpty()) end();
        }

        void end() {
            if (finished) return;
            finished = true;
            showing.remove(this);
            playing.forEach((name, animations) -> {
                var body = bodies.get(name);
                if (body != null) animations.forEach(body::stopAnimation);
            });
            bodies.forEach((name, tracker) -> {
                if (npcs.containsKey(name)) {
                    Bodies.stopWalking(tracker);
                } else {
                    tracker.close();
                }
            });
            cutscene.actors().forEach((name, actor) -> {
                var npc = npcs.get(name);
                if (npc != null && npc.body().isValid()) actor.at(cutscene.duration()).ifPresent(npc.body()::teleport);
            });
            spawned.forEach(Entity::remove);
            kept.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
            kept.clear();
            if (!cancelled) {
                if (skipping) cutscene.runs().stream().filter(cue -> cue.tick() > reached)
                        .sorted(java.util.Comparator.comparingInt(Cutscene.Run::tick)).forEach(cue -> run.accept(cue.action()));
                done.run();
            }
        }
    }

    /** Puts back whatever the cutscenes' explosions tore down, for the next round. */
    public void restore() {
        demolition.restore();
    }

    @Override
    public void close() {
        showing.forEach(show -> show.cancelled = true);
        scenes.close();
        List.copyOf(showing).forEach(Showing::end);
        demolition.close();
        HandlerList.unregisterAll(this);
    }
}
