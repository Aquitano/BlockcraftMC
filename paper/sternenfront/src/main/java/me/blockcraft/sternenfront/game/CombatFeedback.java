package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.combat.DamageDirection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        long shownMarkerUntil;
        Marker shownMarker;
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
        boolean drawnMarked;
        Optional<DamageDirection> drawnHurt = Optional.empty();
        Component hud = Component.empty();

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

        Optional<Marker> takeMarker(long tick) {
            if (tick >= markerUntil || marker == shownMarker && markerUntil == shownMarkerUntil) return Optional.empty();
            shownMarker = marker;
            shownMarkerUntil = markerUntil;
            return Optional.of(marker);
        }

        boolean crosshairChanged(long tick, Optional<DamageDirection> hurt) {
            boolean marked = tick < markerUntil;
            boolean changed = takeMarker(tick).isPresent() || marked != drawnMarked || !hurt.equals(drawnHurt);
            drawnMarked = marked;
            drawnHurt = hurt;
            return changed;
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

        Component reward(Messages messages, boolean detailed) {
            var key = kills > 1 ? "feedback.multikill" : "feedback." + reward.name().toLowerCase(Locale.ROOT);
            if (!detailed && reward == Reward.KILL) key = kills > 1 ? "feedback.compact-multikill" : "feedback.compact-kill";
            return messages.get(key, Placeholder.component("target", target),
                    Formatter.number("points", points), Formatter.number("kills", kills));
        }

        Component compose(Component nextHud, Component feedback, Component fallback, Component separator) {
            if (!nextHud.equals(Component.empty())) {
                hud = nextHud;
            }
            if (feedback.equals(Component.empty())) {
                return hud;
            }
            return hud.equals(Component.empty()) ? feedback.append(fallback) : hud.append(separator).append(feedback);
        }
    }

    private static final char FIRST_DAMAGE_ARC = '\uE228';

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
        return fighter.heat().warning(hotAt, game.factionOf(player).flatMap(fighter::blaster).map(blaster -> game.firing(player, blaster).heat()).orElse(0.0));
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
        boolean detailed = hud.equals(Component.empty()) && state.hud.equals(Component.empty());
        Component line = Component.empty();
        drawCrosshair(player, state, tick);
        if (tick < state.rewardUntil) {
            line = state.reward(game.messages(), detailed);
        }
        if (tick < state.readyUntil && ready) {
            if (!line.equals(Component.empty())) {
                line = line.append(Component.space());
            }
            line = line.append(game.messages().get("feedback.hero-ready"));
        }
        var fallback = Component.empty();
        if (!line.equals(Component.empty())) {
            line = line.append(game.messages().get("feedback.progress", Formatter.number("points", points), Formatter.number("cost", cost)));
            if (detailed && (venting || hot(player))) {
                fallback = fallback.append(Component.space()).append(game.messages().get(venting ? "combat.venting" : "combat.hot",
                        Formatter.number("seconds", (heat.ventTicks(tick) + 19) / 20)));
            }
        }
        var combined = state.compose(hud, line, fallback, game.messages().get("feedback.separator"));
        if (!line.equals(Component.empty())) {
            api.notifications().feedbackNow(player, combined, 5);
        } else if (!combined.equals(Component.empty())) {
            api.notifications().status(player, combined);
        }
    }

    private void drawCrosshair(Player player, State state, long tick) {
        var hurt = game.combat().damageDirection(player);
        if (!state.crosshairChanged(tick, hurt)) {
            return;
        }
        var notifications = BlockcraftApi.get().notifications();
        if (!state.drawnMarked && hurt.isEmpty()) {
            notifications.clearMarker(player);
            return;
        }
        var arc = hurt.map(direction -> UiFont.centredBehind((char) (FIRST_DAMAGE_ARC + direction.ordinal()))).orElse(Component.empty());
        var marker = state.drawnMarked
                ? game.messages().get("hit." + state.marker.name().toLowerCase(Locale.ROOT), GameSidebar.icons(player)) : Component.empty();
        notifications.markerNow(player, arc.append(marker), Math.max(state.markerUntil - tick, game.combat().hurtTicks(player)));
    }

    public void forget(Player player) {
        if (states.remove(player.getUniqueId()) != null) BlockcraftApi.get().notifications().clearMarker(player);
    }

    public void clear() {
        for (var id : states.keySet()) {
            var player = Bukkit.getPlayer(id);
            if (player != null) BlockcraftApi.get().notifications().clearMarker(player);
        }
        states.clear();
    }
}
