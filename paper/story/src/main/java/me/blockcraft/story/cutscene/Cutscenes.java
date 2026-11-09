package me.blockcraft.story.cutscene;

import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.scene.SceneTimeline;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * Plays a {@link Cutscene} to the whole party at once. Each member gets their own camera and may skip alone; the
 * actor and the effects are shared, so they follow whichever member's camera has come furthest, and the story goes
 * on once every member's scene has ended.
 */
public final class Cutscenes implements AutoCloseable {

    private final Plugin plugin;
    private final Messages messages;
    private final Scenes scenes;
    private final int charactersPerTick;
    private final int fadeTicks;

    public Cutscenes(Plugin plugin, Messages messages, int charactersPerTick, int fadeTicks) {
        this.plugin = plugin;
        this.messages = messages;
        this.scenes = new Scenes(plugin);
        this.charactersPerTick = Math.max(1, charactersPerTick);
        this.fadeTicks = Math.max(1, fadeTicks);
    }

    public void play(Cutscene cutscene, List<Player> viewers, Optional<LivingEntity> actor, Runnable done) {
        var showing = new Showing(cutscene, actor, viewers.size(), done);
        if (viewers.isEmpty()) {
            showing.end();
            return;
        }
        for (var viewer : viewers) {
            var scope = new PresentationScope(plugin);
            scope.own(() -> viewer.sendActionBar(Component.empty()));
            var timeline = new SceneTimeline(viewer.getWorld(), cutscene.camera(), List.of(), SceneTimeline.SkipMode.COMPLETE,
                    (player, tick, total, skipped) -> {
                        double fade = Math.max(1.0 - (double) tick / fadeTicks, 1.0 - (double) (total - tick) / fadeTicks);
                        ScreenEffects.frame(scope, player, Math.clamp(fade, 0, 1), true);
                        showing.reach(tick);
                        subtitle(cutscene, player, tick);
                    });
            if (!scenes.play(viewer, timeline, scope, ignored -> showing.ended())) showing.ended();
        }
    }

    public boolean playing(Player player) {
        return scenes.active(player);
    }

    public void stop(Player player) {
        scenes.stop(player);
    }

    private void subtitle(Cutscene cutscene, Player viewer, int tick) {
        cutscene.lineAt(tick).ifPresentOrElse(line -> {
            var text = PlainTextComponentSerializer.plainText().serialize(messages.get(line.text()));
            int shown = Math.min(text.length(), (tick - line.tick() + 1) * charactersPerTick);
            viewer.sendActionBar(messages.get("cutscene.line",
                    Placeholder.component("speaker", messages.get("cutscene.speaker." + line.speaker())),
                    Placeholder.unparsed("text", text.substring(0, shown))));
        }, () -> {
            if (cutscene.lines().stream().anyMatch(line -> line.tick() + line.ticks() == tick)) viewer.sendActionBar(Component.empty());
        });
    }

    private final class Showing {
        private final Cutscene cutscene;
        private final Optional<LivingEntity> actor;
        private final Runnable done;
        private int open;
        private int reached = -1;
        private boolean finished;

        Showing(Cutscene cutscene, Optional<LivingEntity> actor, int viewers, Runnable done) {
            this.cutscene = cutscene;
            this.actor = actor;
            this.open = viewers;
            this.done = done;
        }

        void reach(int tick) {
            while (reached < tick) {
                reached++;
                act(reached);
            }
        }

        private void act(int tick) {
            actor.filter(LivingEntity::isValid).ifPresent(entity -> cutscene.actorAt(tick).ifPresent(at -> {
                entity.teleport(at);
                entity.setRotation(at.getYaw(), at.getPitch());
            }));
            for (var effect : cutscene.effects()) {
                if (effect.tick() != tick) continue;
                var at = effect.at();
                switch (effect.type()) {
                    case EXPLOSION -> {
                        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 0, 0, 0, 0, null, true);
                        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 6, 0.7f);
                    }
                    case FLARE -> {
                        at.getWorld().spawnParticle(Particle.FIREWORK, at, 60, 0.4, 0.4, 0.4, 0.15, null, true);
                        at.getWorld().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 4, 1);
                    }
                }
            }
        }

        void ended() {
            if (--open <= 0) end();
        }

        void end() {
            if (finished) return;
            finished = true;
            actor.filter(LivingEntity::isValid).ifPresent(entity -> cutscene.actorAt(cutscene.duration()).ifPresent(entity::teleport));
            done.run();
        }
    }

    @Override
    public void close() {
        scenes.close();
    }
}
