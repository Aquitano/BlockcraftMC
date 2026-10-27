package me.blockcraft.zombies.game;

import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.arena.StationKind;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The guns an armory sells, turning in a ring above it so they read from across the yard. Each one faces outwards
 * flat, the way a vanilla item lies on a display, so a player without the pack sees the same ring of plain hoes.
 */
final class WeaponRacks {

    private static final int KEYFRAME_TICKS = 10;
    private static final double FULL_TURN = Math.PI * 2;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private record Shown(ItemDisplay display, double offset) { }

    private final ArenaMap arena;
    private final ZombiesConfig.Armory config;
    private final List<Shown> shown = new ArrayList<>();

    WeaponRacks(ArenaMap arena, ZombiesConfig.Armory config) {
        this.arena = arena;
        this.config = config;
    }

    void show(int tick) {
        clear();
        var guns = Weapon.buyable();
        for (var station : arena.stations()) {
            if (station.kind() != StationKind.ARMORY) {
                continue;
            }
            var centre = station.location().toBlockLocation().add(0.5, config.height(), 0.5);
            for (int index = 0; index < guns.size(); index++) {
                var gun = guns.get(index);
                double offset = FULL_TURN * index / guns.size();
                var display = arena.world().spawn(centre, ItemDisplay.class, spawned -> {
                    spawned.setItemStack(gun.model().item().build());
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    spawned.setTransformation(pose(spin(tick) + offset));
                    spawned.setBrightness(LIT);
                    spawned.setPersistent(false);
                });
                shown.add(new Shown(display, offset));
            }
        }
    }

    void tick(int tick) {
        if (tick % KEYFRAME_TICKS != 0) {
            return;
        }
        for (var each : shown) {
            each.display().setInterpolationDelay(0);
            each.display().setInterpolationDuration(KEYFRAME_TICKS);
            each.display().setTransformation(pose(spin(tick + KEYFRAME_TICKS) + each.offset()));
        }
    }

    void clear() {
        shown.forEach(each -> each.display().remove());
        shown.clear();
    }

    private double spin(int tick) {
        return FULL_TURN * tick / config.spinTicks();
    }

    private Transformation pose(double angle) {
        var turn = new Quaternionf().rotateY((float) angle);
        return new Transformation(turn.transform(new Vector3f(0, 0, (float) config.radius())), turn,
                new Vector3f(config.scale()), new Quaternionf());
    }
}
