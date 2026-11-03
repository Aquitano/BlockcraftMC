package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.SternenfrontItem;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.hero.Sabers;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Combat {

    private static final class State {
        private final Guard guard;
        private long nextSwing;
        private long raiseUntil;
        private long leapUntil;
        private boolean jumping;
        private boolean jumped;
        private boolean leaped;

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
            state.raiseUntil = game.tick() + 3;
            state.guard.tick(game.tick(), true);
            player.startUsingItem(EquipmentSlot.HAND);
        }
    }

    public void tick(Player player) {
        var state = state(player);
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
            game.deflected(player, player.getEyeLocation());
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
        var eye = player.getEyeLocation();
        var direction = eye.getDirection();
        var faction = game.factionOf(player).orElseThrow();
        var targets = game.targets().stream().filter(target -> game.hittable(faction, target))
                .filter(target -> target.getWorld().equals(player.getWorld()))
                .filter(target -> CombatArc.hits(eye.toVector(), direction, target.getBoundingBox(), config.meleeRange(), config.meleeAngle()))
                .filter(target -> visible(player, target))
                .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(eye)))
                .limit(config.targets()).toList();
        double damage = game.heroOf(player).filter(Hero.CHAMPION::equals).isPresent()
                ? game.config().relic().damage() : game.heroes().config().saber().damage();
        for (var target : targets) {
            double amount = damage;
            if (target instanceof Player defender) {
                var result = block(defender, target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize(), damage);
                amount *= switch (result) {
                    case PARRY -> 0;
                    case BLOCK -> game.heroes().config().saber().blockedShare();
                    case NONE, BROKEN -> 1;
                };
            }
            if (amount > 0) {
                game.strike(player, target, amount, DamageType.MAGIC);
            }
        }
        for (int step = -2; step <= 2; step++) {
            var point = direction.clone().rotateAroundY(Math.toRadians(step * config.meleeAngle() / 5)).multiply(config.meleeRange() * 0.65);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(point), 1, 0, 0.1, 0, 0);
        }
    }

    private static boolean visible(Player player, LivingEntity target) {
        var eye = player.getEyeLocation();
        var toward = CombatArc.nearest(eye.toVector(), target.getBoundingBox()).subtract(eye.toVector());
        double distance = toward.length();
        return distance < 0.01 || eye.getWorld().rayTraceBlocks(eye, toward.multiply(1 / distance), distance,
                FluidCollisionMode.NEVER, true) == null;
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
        player.clearActiveItem();
    }

    public void clear() {
        states.clear();
    }
}
