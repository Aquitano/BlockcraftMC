package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.SternenfrontItem;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.hero.Sabers;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class Combat {

    private static final class State {
        private final Guard guard;
        private long nextSwing;
        private SaberSweep sweep;
        private boolean reverseSwing;
        private long raiseUntil;
        private long leapUntil;
        private boolean jumping;
        private boolean jumped;
        private boolean leaped;
        private Location hurtFrom;
        private long hurtUntil;

        private State(CombatConfig config) {
            guard = new Guard(config);
        }
    }

    private final SternenfrontGame game;
    private final CombatConfig config;
    private final CombatHud hud;
    private final Map<UUID, State> states = new HashMap<>();

    public Combat(SternenfrontGame game, CombatConfig config) {
        this.game = game;
        this.config = config;
        hud = new CombatHud(game);
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new State(config));
    }

    public CombatConfig config() {
        return config;
    }

    public void damaged(Player player, Location source) {
        if (source != null && player.getWorld().equals(source.getWorld())) {
            var state = state(player);
            state.hurtFrom = source.clone();
            state.hurtUntil = game.tick() + config.damageIndicatorTicks();
        }
    }

    public Optional<String> damageDirection(Player player) {
        var state = state(player);
        if (state.hurtFrom == null || game.tick() >= state.hurtUntil || !state.hurtFrom.getWorld().equals(player.getWorld())) {
            return Optional.empty();
        }
        return DamageDirection.from(player.getEyeLocation().getDirection(), state.hurtFrom.toVector().subtract(player.getLocation().toVector()))
                .map(direction -> direction.name().toLowerCase(Locale.ROOT));
    }

    public int leapCooldown(Player player) {
        return (int) Math.max(0, state(player).leapUntil - game.tick());
    }

    public void leapCooldown(Player player, int ticks) {
        state(player).leapUntil = game.tick() + ticks;
    }

    public static boolean holdsBlade(Player player) {
        var item = player.getInventory().getItemInMainHand();
        return Sabers.isLit(item) || HotbarItem.of(item, SternenfrontItem.class).filter(SternenfrontItem.BLADE::equals).isPresent();
    }

    public void raise(Player player) {
        if (game.onField(player) && holdsBlade(player) && !player.isInsideVehicle()
                && game.heroOf(player).filter(Hero::bladed).isPresent()) {
            var state = state(player);
            state.sweep = null;
            state.raiseUntil = game.tick() + 3;
            state.guard.tick(game.tick(), true);
            player.startUsingItem(EquipmentSlot.HAND);
        }
    }

    public void tick(Player player) {
        var state = state(player);
        advanceSweep(player, state);
        if (player.isInsideVehicle()) {
            state.guard.tick(game.tick(), false);
            return;
        }
        boolean blade = holdsBlade(player) && game.heroOf(player).filter(Hero::bladed).isPresent() && !player.isInsideVehicle();
        boolean holding = blade && (game.tick() < state.raiseUntil
                || player.isHandRaised() && player.getHandRaised() == EquipmentSlot.HAND);
        state.guard.tick(game.tick(), holding);
        boolean jumping = player.getCurrentInput().isJump();
        Entity body = player;
        if (body.isOnGround()) {
            state.jumped = false;
            state.leaped = false;
        }
        if (jumping && !state.jumping) {
            if (state.jumped && !state.leaped && blade) {
                game.leap(player);
                state.leaped = true;
            }
            state.jumped = true;
        }
        state.jumping = jumping;
        if (game.tick() % 4 == 0) {
            hud.show(player, state.guard);
        }
    }

    public Guard.Result block(Player player, Vector incoming, double damage) {
        return block(player, incoming, damage, SoundCue.SABER_DEFLECT);
    }

    private Guard.Result block(Player player, Vector incoming, double damage, SoundCue contact) {
        var state = state(player);
        if (!holdsBlade(player) || player.getEyeLocation().getDirection().dot(incoming.clone().multiply(-1)) < 0.2) {
            return Guard.Result.NONE;
        }
        var result = state.guard.hit(game.tick(), damage);
        if (result == Guard.Result.BROKEN) {
            player.clearActiveItem();
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.8f, 1);
            BlockcraftApi.get().notifications().feedback(player, game.messages().get("combat.guard-broken"));
        } else if (result != Guard.Result.NONE) {
            game.deflected(player, player.getEyeLocation(), contact);
        }
        return result;
    }

    public void swing(Player player) {
        if (!game.onField(player) || !holdsBlade(player) || game.heroOf(player).filter(Hero::bladed).isEmpty()
                || player.isInsideVehicle()) {
            return;
        }
        var state = state(player);
        if (game.tick() < state.nextSwing) {
            return;
        }
        state.nextSwing = game.tick() + config.swingTicks();
        state.raiseUntil = 0;
        player.clearActiveItem();
        state.guard.tick(game.tick(), false);
        game.endProtection(player);
        SoundCue.SABER_SWING.play(player.getLocation(), 0.8f);
        var hero = game.heroOf(player).orElseThrow();
        double damage = hero == Hero.CHAMPION
                ? game.config().relic().damage() : game.heroes().config().saber().damage();
        state.sweep = new SaberSweep(player.getEyeLocation().subtract(0, 0.3, 0), game.heroes().sabers().colour(player, hero),
                damage, config.meleeRange(), config.meleeAngle(), config.sweepTicks(), game.tick(), state.reverseSwing);
        state.reverseSwing = !state.reverseSwing;
        advanceSweep(player, state);
    }

    private void advanceSweep(Player player, State state) {
        var slash = state.sweep;
        if (slash == null) {
            return;
        }
        var origin = slash.origin();
        if (!game.onField(player) || !holdsBlade(player) || player.isInsideVehicle()
                || !player.getWorld().equals(origin.getWorld()) || game.heroOf(player).filter(Hero::bladed).isEmpty()) {
            state.sweep = null;
            return;
        }
        var trailStart = slash.trailStart();
        var rays = slash.advance(game.tick());
        if (rays.isEmpty()) {
            return;
        }
        var faction = game.factionOf(player).orElseThrow();
        var targets = game.targets().stream().filter(target -> game.hittable(faction, target))
                .filter(target -> !game.isProtected(target) && target.getWorld().equals(origin.getWorld())).toList();
        var trail = new ArrayList<SaberCrescent.Sample>();
        trailStart.ifPresent(direction -> {
            var wall = origin.getWorld().rayTraceBlocks(origin, direction, config.meleeRange(), FluidCollisionMode.NEVER, true);
            double reach = wall == null ? config.meleeRange() : Math.max(0, wall.getHitPosition().distance(origin.toVector()) - 0.04);
            trail.add(new SaberCrescent.Sample(direction, reach));
        });
        for (var direction : rays) {
            var wall = origin.getWorld().rayTraceBlocks(origin, direction, config.meleeRange(), FluidCollisionMode.NEVER, true);
            double reach = wall == null ? config.meleeRange() : Math.max(0, wall.getHitPosition().distance(origin.toVector()) - 0.04);
            trail.add(new SaberCrescent.Sample(direction, reach));
            for (var target : targets) {
                if (CombatArc.hits(origin.toVector(), direction, target.getBoundingBox(), reach) && slash.contact(target.getUniqueId())) {
                    strike(player, target, slash.damage());
                }
            }
            if (wall != null) {
                game.effects().saberStrike(player, wall, sweep(origin), slash.colour());
            }
        }
        game.effects().saberSweep(origin, trail, slash.colour());
        if (slash.finished()) {
            state.sweep = null;
        }
    }

    private void strike(Player player, LivingEntity target, double damage) {
        double amount = damage;
        if (target instanceof Player defender) {
            var result = block(defender, target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize(),
                    damage, SoundCue.SABER_CLASH);
            amount *= switch (result) {
                case PARRY -> 0;
                case BLOCK -> game.heroes().config().saber().blockedShare();
                case NONE, BROKEN -> 1;
            };
        }
        if (amount > 0 && game.strike(player, target, amount, DamageType.MAGIC)) {
            SoundCue.SABER_HIT.play(target.getLocation().add(0, target.getHeight() * 0.6, 0), 0.8f);
        }
    }

    /** A swing crosses the view from side to side, whichever way the player looks up or down. */
    private static Vector sweep(Location eye) {
        double yaw = Math.toRadians(eye.getYaw());
        return new Vector(Math.cos(yaw), 0, Math.sin(yaw));
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
        player.clearActiveItem();
    }

    public void clear() {
        states.clear();
    }
}
