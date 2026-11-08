package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.HeatGlow;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The blasters in fighters' hands, drawn in the pose of their last shot or sprint and glowing with their heat. */
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

    HeldBlasters(SternenfrontGame game) {
        this.game = game;
        glow = HeatGlow.Steps.from(game.plugin().getConfig().getConfigurationSection("heat"));
    }

    void fired(LivingEntity shooter, long tick) {
        lastShots.put(shooter.getUniqueId(), new LastShot(shooter, tick));
        unkicked.add(shooter.getUniqueId());
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
                restore(shot);
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
        }
    }

    private void show(LivingEntity actor, long tick) {
        var equipment = actor.getEquipment();
        if (equipment == null || game.animated(actor)) {
            return;
        }
        var shot = lastShots.get(actor.getUniqueId());
        var pose = shot != null && tick - shot.tick() < FIRING_TICKS ? Blaster.Pose.FIRING
                : actor instanceof Player player && game.lowered(player) ? Blaster.Pose.LOWERED : Blaster.Pose.READY;
        var heat = actor instanceof Player player
                ? HeatGlow.of(glow, game.fighter(player).heat().level(), game.fighter(player).heat().venting(tick), tick)
                : HeatGlow.of(glow, game.bots().heat(actor), game.bots().venting(actor, tick), tick);
        var look = new Look(pose, heat, actor instanceof Player player ? player.getInventory().getHeldItemSlot() : -1);
        if (look.equals(shown.put(actor.getUniqueId(), look)) && tick % REFRESH_TICKS != 0) {
            return;
        }
        var main = equipment.getItemInMainHand();
        if (HotbarItem.of(main, Blaster.class).filter(blaster -> blaster.updateLook(main, pose, heat)).isPresent()) {
            equipment.setItemInMainHand(main);
        }
        var off = equipment.getItemInOffHand();
        if (HotbarItem.of(off, Blaster.class).filter(blaster -> blaster.updateLook(off, pose, heat)).isPresent()) {
            equipment.setItemInOffHand(off);
        }
    }

    /** A shooter may have put the gun away mid-shot; the hands show their blasters again the same tick. */
    private static void restore(LastShot shot) {
        if (shot.shooter() instanceof Player player && player.isValid()) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                var item = inventory.getItem(slot);
                if (HotbarItem.of(item, Blaster.class).filter(blaster -> blaster.updateLook(item, Blaster.Pose.READY, HeatGlow.COLD)).isPresent()) {
                    inventory.setItem(slot, item);
                }
            }
        }
    }

    void clear() {
        lastShots.values().forEach(HeldBlasters::restore);
        lastShots.clear();
        unkicked.clear();
        shown.clear();
    }
}
