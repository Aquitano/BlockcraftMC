package me.blockcraft.story.cutscene;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.scene.SceneTimeline;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.common.message.Messages;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.DialoguePanel;
import me.blockcraft.story.dialogue.Voices;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
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

/**
 * Plays a {@link Cutscene} to the whole party at once. Each member gets their own camera, subtitles, voices and sounds
 * and may skip alone; the cast, the craft and the effects are shared, so they follow whichever member's camera has come
 * furthest, and the story goes on once every member's scene has ended. The cast wears BetterModel bodies when
 * BetterModel runs; without it the lead stays a plain figure and the rest of the cast stays away.
 */
public final class Cutscenes implements AutoCloseable {

    private static final int RISE = 6;
    /** A display's moves glide over this many ticks, the longest a teleport may glide being 59. */
    private static final int CRAFT_STEP = 10;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private final Plugin plugin;
    private final Messages messages;
    private final Voices voices;
    private final Scenes scenes;
    private final DialogueHud.Typing typing;
    private final int fadeTicks;
    private final Set<Showing> showing = new HashSet<>();

    public Cutscenes(Plugin plugin, Messages messages, Voices voices, DialogueHud.Typing typing, int fadeTicks) {
        this.plugin = plugin;
        this.messages = messages;
        this.voices = voices;
        this.scenes = new Scenes(plugin);
        this.typing = typing;
        this.fadeTicks = Math.max(1, fadeTicks);
    }

    public void play(Cutscene cutscene, List<Player> viewers, Optional<LivingEntity> lead, Runnable done) {
        var show = new Showing(cutscene, lead, viewers.size(), done);
        showing.add(show);
        if (viewers.isEmpty()) {
            show.end();
            return;
        }
        for (var viewer : viewers) {
            var scope = new PresentationScope(plugin);
            var heard = new int[] {-1};
            scope.own(() -> {
                viewer.sendActionBar(Component.empty());
                viewer.stopSound(SoundStop.source(Sound.Source.VOICE));
                viewer.stopSound(SoundStop.source(Sound.Source.AMBIENT));
            });
            var timeline = new SceneTimeline(viewer.getWorld(), cutscene.camera(), List.of(), SceneTimeline.SkipMode.COMPLETE,
                    (player, tick, total, skipped) -> {
                        double fade = Math.max(1.0 - (double) tick / fadeTicks, 1.0 - (double) (total - tick) / fadeTicks);
                        ScreenEffects.frame(scope, player, Math.clamp(fade, 0, 1), true);
                        show.reach(tick);
                        while (heard[0] < tick) hear(cutscene, player, ++heard[0]);
                        subtitle(cutscene, player, tick);
                    });
            if (!scenes.play(viewer, timeline, scope, ignored -> show.ended())) show.ended();
        }
    }

    public boolean playing(Player player) {
        return scenes.active(player);
    }

    public void stop(Player player) {
        scenes.stop(player);
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

    /** The line in the dialogue panel, typing out as a conversation's does. */
    private void subtitle(Cutscene cutscene, Player viewer, int tick) {
        cutscene.lineAt(tick).ifPresentOrElse(line -> {
            var page = DialoguePanel.pages(plain(messages.get(line.text()))).getFirst();
            int age = tick - line.tick();
            viewer.sendActionBar(DialoguePanel.render(new DialoguePanel.View(plain(messages.get("cutscene.speaker." + line.speaker())),
                    DialoguePanel.revealed(page, (int) ((age + 1) * typing.rate(DialoguePanel.length(page)))), false, List.of(), 0, "",
                    Math.max(0, RISE - age * 2), false), new DialoguePanel.Size(page.size(), 0, false)));
        }, () -> {
            if (cutscene.lines().stream().anyMatch(line -> line.tick() + line.ticks() == tick)) viewer.sendActionBar(Component.empty());
        });
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static boolean modelled() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel");
    }

    private final class Showing {
        private final Cutscene cutscene;
        private final Optional<LivingEntity> lead;
        private final Runnable done;
        private final Map<String, LivingEntity> cast = new HashMap<>();
        private final Map<String, EntityTracker> bodies = new HashMap<>();
        private final Map<String, Boolean> walking = new HashMap<>();
        private final Map<String, Set<String>> playing = new HashMap<>();
        private final List<Entity> spawned = new ArrayList<>();
        private final Map<Cutscene.Craft, ItemDisplay> flying = new HashMap<>();
        private int open;
        private int reached = -1;
        private boolean finished;

        Showing(Cutscene cutscene, Optional<LivingEntity> lead, int viewers, Runnable done) {
            this.cutscene = cutscene;
            this.lead = lead;
            this.open = viewers;
            this.done = done;
        }

        void reach(int tick) {
            while (reached < tick && !finished) {
                reached++;
                act(reached);
            }
        }

        private void act(int tick) {
            if (tick == 0) cast();
            cutscene.actors().forEach((name, actor) -> {
                var entity = cast.get(name);
                if (entity == null || !entity.isValid()) return;
                actor.at(tick).ifPresent(at -> {
                    entity.teleport(at);
                    entity.setRotation(at.getYaw(), at.getPitch());
                });
                var body = bodies.get(name);
                if (body == null) return;
                boolean moving = actor.walking(tick);
                if (!Boolean.valueOf(moving).equals(walking.put(name, moving))) {
                    if (moving) {
                        body.animate("walk");
                    } else {
                        body.stopAnimation("walk");
                    }
                }
                for (var gesture : actor.gestures()) {
                    if (gesture.tick() != tick) continue;
                    if (gesture.stop()) {
                        body.stopAnimation(gesture.animation());
                    } else {
                        body.animate(gesture.animation(), AnimationModifier.DEFAULT);
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
        }

        /** The lead plays in their own body; everyone else is spawned in theirs, and only where BetterModel draws it. */
        private void cast() {
            cutscene.actors().forEach((name, actor) -> {
                if (name.equals(Cutscene.LEAD)) {
                    lead.filter(LivingEntity::isValid).ifPresent(entity -> {
                        cast.put(name, entity);
                        dress(name, entity, actor);
                    });
                    return;
                }
                if (!modelled() || actor.model().isBlank()) return;
                actor.at(0).ifPresent(at -> {
                    var extra = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
                        mannequin.setPersistent(false);
                        mannequin.setImmovable(true);
                        mannequin.setInvulnerable(true);
                        mannequin.setGravity(false);
                    });
                    spawned.add(extra);
                    cast.put(name, extra);
                    dress(name, extra, actor);
                });
            });
        }

        private void dress(String name, LivingEntity entity, Cutscene.Actor actor) {
            if (!modelled() || actor.model().isBlank()) return;
            BetterModel.model(actor.model()).ifPresent(model -> {
                var body = model.getOrCreate(BukkitAdapter.adapt(entity));
                bodies.put(name, body);
                if (!actor.idle().isBlank()) {
                    body.animate(actor.idle(), AnimationModifier.DEFAULT);
                    playing.computeIfAbsent(name, ignored -> new HashSet<>()).add(actor.idle());
                }
            });
        }

        private void effect(Cutscene.Effect effect) {
            var at = effect.at();
            switch (effect.type()) {
                case EXPLOSION -> {
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
            if (age < 0 || age > craft.ticks()) return;
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
            if (age == craft.ticks()) {
                ship.remove();
                flying.remove(craft);
                return;
            }
            if (age % CRAFT_STEP == 0) {
                double ahead = Math.min(1, (double) (age + CRAFT_STEP) / craft.ticks());
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

        void ended() {
            if (--open <= 0) end();
        }

        void end() {
            if (finished) return;
            finished = true;
            showing.remove(this);
            playing.forEach((name, animations) -> {
                var body = bodies.get(name);
                if (body != null) animations.forEach(body::stopAnimation);
            });
            var body = bodies.get(Cutscene.LEAD);
            if (body != null) body.stopAnimation("walk");
            var leadActor = cutscene.actors().get(Cutscene.LEAD);
            if (leadActor != null) {
                lead.filter(LivingEntity::isValid).ifPresent(entity -> leadActor.at(cutscene.duration()).ifPresent(entity::teleport));
            }
            bodies.forEach((name, tracker) -> {
                if (!name.equals(Cutscene.LEAD)) tracker.close();
            });
            spawned.forEach(Entity::remove);
            done.run();
        }
    }

    @Override
    public void close() {
        scenes.close();
        List.copyOf(showing).forEach(Showing::end);
    }
}
