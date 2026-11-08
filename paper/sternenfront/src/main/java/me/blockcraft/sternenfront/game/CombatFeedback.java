package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.combat.DamageDirection;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class CombatFeedback {

    public enum HitMarker {
        HIT(SoundCue.HITMARKER), HEADSHOT(SoundCue.HEADSHOT), KILL(SoundCue.KILL);

        final SoundCue sound;

        HitMarker(SoundCue sound) {
            this.sound = sound;
        }
    }

    public enum Reward { KILL, ASSIST, DEFENCE, HEALING, CAPTURE, NEUTRALISE, HEADSHOT, CHAIN }

    /** Under the crosshair in place of the spread: the gun is down from a sprint, or has just come up and can fire. */
    enum Readiness { LOWERED, RAISED }

    /**
     * What the combat HUD puts on the action bar: {@code meter}, {@code meterWidth} pixels wide, centred under the
     * crosshair, and the {@code abilities} panel, which takes no room and which the pack's shader moves into the
     * bottom-right corner from the line's middle.
     */
    public record ActionBar(Component meter, int meterWidth, Component abilities) {
        static final ActionBar NONE = new ActionBar(Component.empty(), 0, Component.empty());
    }

    static final class PlayerFeedback {
        HitMarker marker;
        long markerUntil;
        long shownMarkerUntil;
        HitMarker shownMarker;
        long soundedAt = Long.MIN_VALUE;
        HitMarker sounded;
        Reward reward;
        int points;
        int kills;
        boolean headshot;
        int chain;
        long chainUntil;
        int shownChain;
        long rewardUntil;
        Component notice = Component.empty();
        long noticeUntil;
        boolean ready;
        long readyUntil;
        boolean eliteReady;
        long eliteReadyUntil;
        boolean venting;
        boolean drawnMarked;
        Optional<DamageDirection> drawnHurt = Optional.empty();
        long drawnHurtUntil;
        Optional<Character> drawnAim = Optional.empty();
        long aimRefreshAt;
        boolean lowered;
        long raisedUntil;
        ActionBar actionBar = ActionBar.NONE;

        boolean eliteAvailable(boolean available, long tick, int duration) {
            boolean announce = available && !eliteReady;
            if (announce) eliteReadyUntil = tick + duration;
            eliteReady = available;
            return announce;
        }

        boolean hit(HitMarker next, long tick, int ticks) {
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

        Optional<HitMarker> takeMarker(long tick) {
            if (tick >= markerUntil || marker == shownMarker && markerUntil == shownMarkerUntil) return Optional.empty();
            shownMarker = marker;
            shownMarkerUntil = markerUntil;
            return Optional.of(marker);
        }

        /**
         * Whether the gun in hand is down from a sprint, or came up within the last {@code raisedTicks}. Putting the gun
         * away while it is down shows nothing coming up.
         */
        Optional<Readiness> readiness(boolean aiming, boolean lowered, long tick, int raisedTicks) {
            boolean down = aiming && lowered;
            if (this.lowered && !down && aiming) {
                raisedUntil = tick + raisedTicks;
            }
            this.lowered = down;
            return down ? Optional.of(Readiness.LOWERED) : aiming && tick < raisedUntil ? Optional.of(Readiness.RAISED) : Optional.empty();
        }

        /**
         * {@code aim} is the glyph round the crosshair, the spread or the gun's readiness. A shown one is sent again before
         * its title runs out, since the title is all that keeps it on screen.
         */
        boolean crosshairChanged(long tick, Optional<DamageDirection> hurt, long hurtUntil, Optional<Character> aim) {
            boolean marked = tick < markerUntil;
            boolean changed = takeMarker(tick).isPresent() || marked != drawnMarked || !hurt.equals(drawnHurt) || hurtUntil != drawnHurtUntil
                    || !aim.equals(drawnAim) || aim.isPresent() && tick >= aimRefreshAt;
            drawnMarked = marked;
            drawnHurt = hurt;
            drawnHurtUntil = hurtUntil;
            drawnAim = aim;
            if (changed) {
                aimRefreshAt = tick + AIM_REFRESH_TICKS;
            }
            return changed;
        }

        void earned(Reward next, int amount, long tick, int ticks) {
            if (tick >= rewardUntil) {
                points = 0;
                kills = 0;
                headshot = false;
                shownChain = 0;
                reward = next;
            }
            points += amount;
            switch (next) {
                case KILL -> kills++;
                case HEADSHOT -> headshot = true;
                default -> { }
            }
            if (reward != Reward.KILL || next == Reward.KILL) {
                reward = next;
            }
            rewardUntil = tick + ticks;
            noticeUntil = 0;
        }

        /** How many kills in a row this one makes, each within {@code window} ticks of the one before. */
        int chained(long tick, int window) {
            chain = tick < chainUntil ? chain + 1 : 1;
            chainUntil = tick + window;
            shownChain = chain;
            return chain;
        }

        Component reward(Messages messages) {
            int inARow = Math.max(kills, shownChain);
            var key = inARow > 1 ? "feedback.multikill"
                    : headshot ? "feedback.headshot"
                    : "feedback." + reward.name().toLowerCase(Locale.ROOT);
            return messages.get(key, Formatter.number("points", points), Formatter.number("kills", inARow));
        }

        /** The meter centred under the crosshair, the popup left of it, and the abilities panel in the line's middle. */
        Component compose(Component feedback, Optional<Component> course) {
            var line = UiFont.rightAligned(feedback, REWARD_WIDTH).append(Component.space());
            if (course.isPresent()) {
                return UiFont.measured(line.append(course.get()));
            }
            int side = Math.max(REWARD_WIDTH, UiFont.width(feedback)) + UiFont.width(Component.space());
            return UiFont.centredOn(UiFont.measured(line.append(actionBar.meter()).append(UiFont.space(side))), 2 * side + actionBar.meterWidth(),
                    actionBar.abilities());
        }
    }

    private static final char FIRST_DAMAGE_ARC = '\uE228';
    private static final char FIRST_CROSSHAIR_MARK = '\uE23A';
    private static final char FIRST_SPREAD_TICKS = '\uE250';
    private static final char FIRST_READINESS = '\uE255';
    private static final int AIM_REFRESH_TICKS = 20;
    /**
     * The client counts the title down in its own ticks, so twice the refresh keeps the ticks up while the server runs at
     * half speed; any longer and they would linger into a cinematic, which stops the refresh.
     */
    private static final int AIM_HOLD_TICKS = 40;
    /** Wide enough for the longest popup, so the line does not slide when one appears. */
    private static final int REWARD_WIDTH = 104;
    private static final double LOW_HEALTH = 0.4;
    private static final double LOW_HEALTH_REDNESS = 0.8;
    private static final double HIT_FLASH_TICKS = 10;
    private static final double HIT_REDNESS = 0.7;

    private final SternenfrontGame game;
    private final Map<UUID, PlayerFeedback> players = new HashMap<>();
    private final int markerTicks;
    private final int rewardTicks;
    private final int heroReadyTicks;
    private final int eliteReadyTicks;
    private final int noticeTicks;
    private final int raisedTicks;
    private final double hotAt;
    private final float hitVolume;
    private final float headshotVolume;
    private final float killVolume;
    private final int multikillTicks;
    private final WeaponHandling handling;
    private final SpreadCrosshair spreadCrosshair;
    private Function<Player, Optional<Component>> courseStatus = player -> Optional.empty();

    public CombatFeedback(SternenfrontGame game) {
        this.game = game;
        var config = game.plugin().getConfig();
        markerTicks = Math.clamp(config.getInt("combat-feedback.marker-ticks", 6), 1, 20);
        rewardTicks = Math.clamp(config.getInt("combat-feedback.reward-ticks", 30), 1, 100);
        heroReadyTicks = Math.clamp(config.getInt("combat-feedback.hero-ready-ticks", 60), 1, 200);
        eliteReadyTicks = Math.clamp(config.getInt("combat-feedback.elite-ready-ticks"), 1, 200);
        noticeTicks = Math.clamp(config.getInt("combat-feedback.notice-ticks"), 1, 100);
        raisedTicks = Math.clamp(config.getInt("combat-feedback.raised-ticks"), 1, 40);
        hotAt = Math.clamp(config.getDouble("heat.warning-level", 0.8), 0.1, 0.99);
        hitVolume = (float) Math.clamp(config.getDouble("combat-feedback.sound.hit", 0.65), 0, 1);
        headshotVolume = (float) Math.clamp(config.getDouble("combat-feedback.sound.headshot", 0.8), 0, 1);
        killVolume = (float) Math.clamp(config.getDouble("combat-feedback.sound.kill", 1.0), 0, 1);
        multikillTicks = Math.clamp(config.getInt("combat-feedback.multikill-seconds", 4), 1, 30) * SternenfrontGame.TICKS_PER_SECOND;
        handling = game.handling();
        spreadCrosshair = new SpreadCrosshair(config.getDoubleList("combat-feedback.spread-steps"));
    }

    private PlayerFeedback state(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerFeedback());
    }

    public void courseStatus(Function<Player, Optional<Component>> status) {
        courseStatus = status;
    }

    public boolean hot(Player player) {
        var fighter = game.fighter(player);
        return fighter.heat().warning(hotAt, game.factionOf(player).flatMap(fighter::blaster).map(blaster -> game.firing(player, blaster).heat()).orElse(0.0));
    }

    public void hit(Player player, HitMarker marker) {
        if (state(player).hit(marker, game.tick(), markerTicks)) {
            marker.sound.playTo(player, switch (marker) {
                case HIT -> hitVolume;
                case HEADSHOT -> headshotVolume;
                case KILL -> killVolume;
            });
        }
        refresh(player);
    }

    /** Counts a kill towards a chain of them, with a chime that climbs as it goes on. */
    public int chained(Player player) {
        int chain = state(player).chained(game.tick(), multikillTicks);
        if (chain > 1) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f,
                    (float) Math.min(2, 0.9 + (chain - 2) * 0.2));
        }
        return chain;
    }

    public void earned(Player player, int points, Reward reward) {
        if (points > 0) {
            state(player).earned(reward, points, game.tick(), rewardTicks);
            refresh(player);
        }
    }

    /**
     * A line where a reward pops up, for a key that did nothing, such as an ability still recharging. It joins the combat
     * HUD, whereas a notice of its own would take the action bar and with it the meter and the ability tiles.
     */
    public void notice(Player player, Component message) {
        var state = state(player);
        state.notice = message;
        state.noticeUntil = game.tick() + noticeTicks;
        refresh(player);
    }

    public void aim(Player player) {
        if (!hidden(player)) {
            drawCrosshair(player, state(player), game.tick());
        }
    }

    /** Whether a dialogue, a screen effect or a scene has the screen, which every part of the HUD then leaves to it. */
    public static boolean covered(Player player) {
        var api = BlockcraftApi.get();
        return api.dialogues().active(player) || api.visuals().active(player) || Scenes.isViewing(player);
    }

    private boolean hidden(Player player) {
        return !game.onField(player) || covered(player);
    }

    public void show(Player player, ActionBar actionBar) {
        state(player).actionBar = actionBar;
        refresh(player);
    }

    /** Draws the action bar again with the combat HUD it last showed. */
    public void refresh(Player player) {
        if (hidden(player)) {
            return;
        }
        var api = BlockcraftApi.get();
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
        boolean eliteReady = game.heroOf(player).isEmpty() && game.reinforcementOf(player).isEmpty()
                && game.factionOf(player).filter(faction -> game.reinforcementUnits().roster()
                .affordable(player.getUniqueId(), faction, Reinforcement.of(faction))).isPresent();
        boolean eliteAnnounced = state.eliteAvailable(eliteReady, tick, eliteReadyTicks);
        if (ready && !state.ready) {
            state.readyUntil = tick + heroReadyTicks;
        }
        if (ready && !state.ready || eliteAnnounced) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.7f, 1.4f);
        }
        state.ready = ready;
        drawCrosshair(player, state, tick);
        var line = tick < state.noticeUntil ? state.notice
                : tick < state.rewardUntil ? state.reward(game.messages())
                : ready && tick < state.readyUntil ? game.messages().get("feedback.hero-ready")
                : eliteReady && tick < state.eliteReadyUntil ? game.messages().get("feedback.elite-ready")
                : Component.empty();
        ScreenEffects.hurt(player, hurtIntensity(player, game.combat().hurtTicks(player)));
        var combined = state.compose(line, courseStatus.apply(player));
        if (tick < state.rewardUntil) {
            api.notifications().feedbackNow(player, combined, 5);
        } else {
            api.notifications().status(player, combined);
        }
    }

    /** The edges of the screen redden with a hit and stay red while health is low. */
    private double hurtIntensity(Player player, int hurtTicksLeft) {
        double share = player.getHealth() / player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double low = share < LOW_HEALTH ? (LOW_HEALTH - share) / LOW_HEALTH * LOW_HEALTH_REDNESS : 0;
        double sinceHit = game.combat().config().damageIndicatorTicks() - hurtTicksLeft;
        double hit = hurtTicksLeft > 0 ? Math.max(0, 1 - sinceHit / HIT_FLASH_TICKS) * HIT_REDNESS : 0;
        return Math.max(low, hit);
    }

    private void drawCrosshair(Player player, PlayerFeedback state, long tick) {
        var hurt = game.combat().damageDirection(player);
        boolean aiming = aiming(player);
        var aim = state.readiness(aiming, aiming && game.lowered(player), tick, raisedTicks)
                .map(readiness -> (char) (FIRST_READINESS + readiness.ordinal()))
                .or(() -> aiming ? spreadSize(player).map(size -> (char) (FIRST_SPREAD_TICKS + size)) : Optional.empty());
        if (!state.crosshairChanged(tick, hurt, hurt.isPresent() ? tick + game.combat().hurtTicks(player) : 0, aim)) {
            return;
        }
        var notifications = BlockcraftApi.get().notifications();
        if (!state.drawnMarked && hurt.isEmpty() && aim.isEmpty()) {
            notifications.clearMarker(player);
            return;
        }
        var ticks = aim.map(UiFont::centredBehind).orElse(Component.empty());
        var arc = hurt.map(direction -> UiFont.centredBehind((char) (FIRST_DAMAGE_ARC + direction.ordinal()))).orElse(Component.empty());
        var marker = state.drawnMarked ? UiFont.centredBehind((char) (FIRST_CROSSHAIR_MARK + state.marker.ordinal())) : Component.empty();
        long held = Math.max(Math.max(state.markerUntil - tick, game.combat().hurtTicks(player)), aim.isPresent() ? AIM_HOLD_TICKS : 0);
        notifications.markerNow(player, ticks.append(arc).append(marker), held);
    }

    /** A blaster in hand, on foot and unscoped, for a client whose pack draws the marks round the crosshair. */
    private boolean aiming(Player player) {
        return BlockcraftApi.get().resourcePack().applied(player) && !player.isInsideVehicle() && !game.scoped(player)
                && HotbarItem.of(player.getInventory().getItemInMainHand(), Blaster.class).isPresent();
    }

    private Optional<Integer> spreadSize(Player player) {
        var fighter = game.fighter(player);
        return game.factionOf(player).flatMap(fighter::blaster).flatMap(blaster -> {
            double base = game.config().stats(blaster).spread();
            return spreadCrosshair.size(base, handling.spread(blaster, base, WeaponHandling.Posture.of(player), fighter.heat().level()));
        });
    }

    public void forget(Player player) {
        ScreenEffects.hurt(player, 0);
        if (players.remove(player.getUniqueId()) != null) BlockcraftApi.get().notifications().clearMarker(player);
    }

    /** Off the field the HUD stops updating, so the redness would stay until the next deployment. */
    public void offField(Player player) {
        ScreenEffects.hurt(player, 0);
    }

    public void clear() {
        for (var id : players.keySet()) {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                ScreenEffects.hurt(player, 0);
                BlockcraftApi.get().notifications().clearMarker(player);
            }
        }
        players.clear();
    }
}
