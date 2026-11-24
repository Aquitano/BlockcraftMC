package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.HeatGlow;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The blasters in fighters' hands, drawn in the pose of their last shot or sprint and glowing with their heat.
 * <p>
 * A player's own item never takes the firing pose: a client that holds its trigger starts breaking its trigger block
 * again whenever the held item changes, with the block's hit sound each time. Their kick comes from a cooldown on the
 * item, which their client counts down itself, and everyone else is sent the kicked item as that player's equipment.
 * For the same reason a held trigger keeps a venting gun's glow from pulsing in its holder's hand.
 */
public final class HeldBlasters {

    private static final int FIRING_TICKS = 3;
    /** A respawn's loadout or a swap between hands changes the items but not their look, so it is redrawn this often. */
    private static final int REFRESH_TICKS = 20;

    private record LastShot(LivingEntity shooter, long tick) { }

    /** What {@link #show} last put in an actor's hands; until it changes the items need no look. */
    private record Look(Blaster.Pose pose, HeatGlow heat, int slot) { }

    private final SternenfrontGame game;
    private final HeatGlow.Steps glow;
    private final Map<UUID, LastShot> lastShots = new HashMap<>();
    /** Shooters whose last shot no body has kicked back for yet. */
    private final Set<UUID> unkicked = new HashSet<>();
    private final Map<UUID, Look> shown = new HashMap<>();
    /** What other players were last sent as a player's main hand, which is the item itself or that item kicked back. */
    private final Map<UUID, Look> sentToOthers = new HashMap<>();

    HeldBlasters(SternenfrontGame game) {
        this.game = game;
        glow = HeatGlow.Steps.from(game.plugin().getConfig().getConfigurationSection("heat"));
    }

    void fired(LivingEntity shooter, long tick) {
        lastShots.put(shooter.getUniqueId(), new LastShot(shooter, tick));
        unkicked.add(shooter.getUniqueId());
        if (shooter instanceof Player player) {
            player.setCooldown(player.getInventory().getItemInMainHand(), FIRING_TICKS);
        }
        show(shooter, tick);
    }

    /** True once for each shot, for the body that kicks back with it, whichever runs first in a tick. */
    public boolean kick(LivingEntity actor) {
        return unkicked.remove(actor.getUniqueId());
    }

    public double recoil(LivingEntity actor) {
        var shot = lastShots.get(actor.getUniqueId());
        return shot == null ? 0 : Math.clamp(1 - (game.tick() - shot.tick()) / (double) FIRING_TICKS, 0, 1);
    }

    void tick(long tick) {
        lastShots.values().removeIf(shot -> {
            if (!shot.shooter().isValid() || tick - shot.tick() >= FIRING_TICKS) {
                unkicked.remove(shot.shooter().getUniqueId());
                return true;
            }
            return false;
        });
        var actors = game.combatants();
        actors.forEach(actor -> show(actor, tick));
        if (tick % REFRESH_TICKS == 0) {
            var present = actors.stream().map(LivingEntity::getUniqueId).collect(Collectors.toSet());
            shown.keySet().retainAll(present);
            sentToOthers.keySet().retainAll(present);
        }
    }

    private void show(LivingEntity actor, long tick) {
        var equipment = actor.getEquipment();
        if (equipment == null || game.animated(actor)) {
            return;
        }
        var shot = lastShots.get(actor.getUniqueId());
        boolean firing = shot != null && tick - shot.tick() < FIRING_TICKS;
        if (actor instanceof Player player) {
            show(player, firing, tick);
            return;
        }
        var look = new Look(firing ? Blaster.Pose.FIRING : Blaster.Pose.READY,
                HeatGlow.of(glow, game.bots().heat(actor), game.bots().venting(actor, tick), tick), -1);
        if (look.equals(shown.put(actor.getUniqueId(), look)) && tick % REFRESH_TICKS != 0) {
            return;
        }
        var main = equipment.getItemInMainHand();
        if (drawn(main, look)) {
            equipment.setItemInMainHand(main);
        }
        var off = equipment.getItemInOffHand();
        if (drawn(off, look)) {
            equipment.setItemInOffHand(off);
        }
    }

    private void show(Player player, boolean firing, long tick) {
        var fighter = game.fighter(player);
        var heat = fighter.heat();
        boolean held = fighter.trigger().held(tick);
        int slot = player.getInventory().getHeldItemSlot();
        var pose = game.lowered(player) ? Blaster.Pose.LOWERED : Blaster.Pose.READY;
        var own = new Look(pose, HeatGlow.of(glow, heat.level(), heat.venting(tick) && !held, tick), slot);
        var inventory = player.getInventory();
        if (!own.equals(shown.put(player.getUniqueId(), own)) || tick % REFRESH_TICKS == 0) {
            var main = inventory.getItemInMainHand();
            if (drawn(main, own)) {
                inventory.setItemInMainHand(main);
            }
            var off = inventory.getItemInOffHand();
            if (drawn(off, own)) {
                inventory.setItemInOffHand(off);
            }
        }
        var others = new Look(firing ? Blaster.Pose.FIRING : pose, HeatGlow.of(glow, heat.level(), heat.venting(tick), tick), slot);
        var before = sentToOthers.put(player.getUniqueId(), others);
        boolean differs = !others.equals(own);
        if (before == null ? differs : !others.equals(before) || differs && tick % REFRESH_TICKS == 0) {
            var seen = inventory.getItemInMainHand().clone();
            drawn(seen, others);
            player.getTrackedBy().forEach(viewer -> viewer.sendEquipmentChange(player, EquipmentSlot.HAND, seen));
        }
    }

    private static boolean drawn(ItemStack item, Look look) {
        return HotbarItem.of(item, Blaster.class).filter(blaster -> blaster.updateLook(item, look.pose(), look.heat())).isPresent();
    }

    void clear() {
        lastShots.clear();
        unkicked.clear();
        shown.clear();
        sentToOthers.clear();
    }
}
