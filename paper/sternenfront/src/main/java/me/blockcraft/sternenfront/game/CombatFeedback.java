package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.scene.Scenes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CombatFeedback {

    public enum Marker {
        HIT(SoundCue.HITMARKER), HEADSHOT(SoundCue.HEADSHOT), KILL(SoundCue.KILL);

        final SoundCue sound;

        Marker(SoundCue sound) {
            this.sound = sound;
        }
    }

    public enum Reward { KILL, ASSIST, DEFENCE, HEALING, CAPTURE, NEUTRALISE }

    static final class State {
        Marker marker;
        long markerUntil;
        long soundedAt = Long.MIN_VALUE;
        Marker sounded;
        Reward reward;
        Component target = Component.empty();
        int points;
        int kills;
        long rewardUntil;
        boolean ready;
        long readyUntil;
        boolean venting;

        boolean hit(Marker next, long tick, int ticks) {
            if (tick >= markerUntil || next.ordinal() >= marker.ordinal()) {
                marker = next;
                markerUntil = tick + ticks;
            }
            boolean play = soundedAt != tick || next.ordinal() > sounded.ordinal();
            if (play) {
                soundedAt = tick;
                sounded = next;
            }
            return play;
        }

        void earned(Reward next, Component target, int amount, long tick, int ticks) {
            if (tick >= rewardUntil) {
                points = 0;
                kills = 0;
                reward = next;
            }
            points += amount;
            if (next == Reward.KILL) {
                kills++;
            }
            if (reward != Reward.KILL || next == Reward.KILL) {
                reward = next;
                this.target = target;
            }
            rewardUntil = tick + ticks;
        }
    }

    private final SternenfrontGame game;
    private final Map<UUID, State> states = new HashMap<>();
    private final int markerTicks;
    private final int rewardTicks;
    private final double hotAt;
    private final float hitVolume;
    private final float headshotVolume;
    private final float killVolume;

    public CombatFeedback(SternenfrontGame game) {
        this.game = game;
        var config = game.plugin().getConfig();
        markerTicks = Math.clamp(config.getInt("combat-feedback.marker-ticks", 6), 1, 20);
        rewardTicks = Math.clamp(config.getInt("combat-feedback.reward-ticks", 30), 1, 100);
        hotAt = Math.clamp(config.getDouble("heat.warning-level", 0.8), 0.1, 0.99);
        hitVolume = (float) Math.clamp(config.getDouble("combat-feedback.sound.hit", 0.65), 0, 1);
        headshotVolume = (float) Math.clamp(config.getDouble("combat-feedback.sound.headshot", 0.8), 0, 1);
        killVolume = (float) Math.clamp(config.getDouble("combat-feedback.sound.kill", 0.9), 0, 1);
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
    }

    public boolean hot(Player player) {
        var fighter = game.fighter(player);
        return fighter.heat().warning(hotAt, fighter.blaster().map(blaster -> game.firing(player, blaster).heat()).orElse(0.0));
    }

    public void hit(Player player, Marker marker) {
        if (state(player).hit(marker, game.tick(), markerTicks)) {
            marker.sound.playTo(player, switch (marker) {
                case HIT -> hitVolume;
                case HEADSHOT -> headshotVolume;
                case KILL -> killVolume;
            });
        }
        show(player, Component.empty());
    }

    public void earned(Player player, int points, Reward reward, Component target) {
        if (points > 0) {
            state(player).earned(reward, target, points, game.tick(), rewardTicks);
            show(player, Component.empty());
        }
    }

    public void show(Player player, Component hud) {
        var api = BlockcraftApi.get();
        if (!game.onField(player) || api.dialogues().active(player) || api.visuals().active(player) || Scenes.isViewing(player)) {
            return;
        }
        var state = state(player);
        long tick = game.tick();
        var heat = game.fighter(player).heat();
        boolean venting = heat.venting(tick);
        if (state.venting && !venting) {
            SoundCue.COOLED.playTo(player, 0.6f);
        }
        state.venting = venting;
        int points = game.heroes().points(player);
        int cost = game.factionOf(player).map(game.heroes()::cost).orElse(game.heroes().config().cost());
        boolean ready = points >= cost && game.heroOf(player).isEmpty()
                && game.factionOf(player).filter(game.heroes()::available).isPresent();
        if (ready && !state.ready) {
            state.readyUntil = tick + rewardTicks;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.7f, 1.4f);
        }
        state.ready = ready;
        var line = Component.empty();
        if (tick < state.markerUntil) {
            line = line.append(game.messages().get("hit." + state.marker.name().toLowerCase(Locale.ROOT), GameSidebar.icons(player)));
        }
        if (tick < state.rewardUntil) {
            var key = state.kills > 1 ? "feedback.multikill" : "feedback." + state.reward.name().toLowerCase(Locale.ROOT);
            line = line.append(Component.space()).append(game.messages().get(key,
                    Placeholder.component("target", state.target), Formatter.number("points", state.points), Formatter.number("kills", state.kills)));
        }
        if (tick < state.readyUntil && ready) {
            line = line.append(Component.space()).append(game.messages().get("feedback.hero-ready"));
        }
        if (!line.equals(Component.empty())) {
            if (venting || hot(player)) {
                line = line.append(Component.space()).append(game.messages().get(venting ? "combat.venting" : "combat.hot",
                        Formatter.number("seconds", (heat.ventTicks(tick) + 19) / 20)));
            }
            line = line.append(game.messages().get("feedback.progress", Formatter.number("points", points), Formatter.number("cost", cost)));
            api.notifications().feedbackNow(player, line, 5);
        } else if (!hud.equals(Component.empty())) {
            api.notifications().status(player, hud);
        }
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
    }

    public void clear() {
        states.clear();
    }
}
