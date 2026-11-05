package me.blockcraft.sternenfront.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.sternenfront.appearance.Displays;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Short-lived models in the world: each spawns at its first size, grows and travels to its last, optionally shrinks
 * away and removes itself. {@code limit} is a hard cap on how many live at once. Small repeated effects (flashes,
 * tracers) are refused at the cap, so a firefight cannot flood clients; larger ones push the oldest effect out.
 */
public final class Flares {

    private static final int FIRST_MOVE_AGE = 1;
    /** The most ticks the client interpolates one teleport over. */
    private static final int LONGEST_GLIDE = 59;
    private static final float GONE = 0.001f;
    /** Flat fx models lie in their XY plane; the item renderer's half turn keeps them there. */
    private static final Vector3f FLAT_MODEL_NORMAL = new Vector3f(0, 0, 1);
    private static final Quaternionf UNTURNED = new Quaternionf();
    private static final Vector3f FLAT_MODEL_LENGTH = new Vector3f(1, 0, 0);
    private static final float HEAD_ON = 1e-4f;
    private static final float LENGTH_LEFT_COOLED = 0.4f;

    private record Plan(ItemStack look, Quaternionf turn, Vector3f from, Vector3f to, Vector move, int ticks,
                        int fadeTicks, Display.Billboard billboard, boolean lit, Block anchor, Quaternionf finish) {

        Plan(ItemStack look, Quaternionf turn, Vector3f from, Vector3f to, Vector move, int ticks, int fadeTicks,
             Display.Billboard billboard, boolean lit, Block anchor) {
            this(look, turn, from, to, move, ticks, fadeTicks, billboard, lit, anchor, new Quaternionf());
        }

        Transformation at(Vector3f scale) {
            return new Transformation(new Vector3f(), new Quaternionf(turn), new Vector3f(scale), new Quaternionf(finish));
        }

        boolean changes() {
            return !from.equals(to) || move.lengthSquared() > 0;
        }
    }

    private static final class Flare {
        private final ItemDisplay display;
        private final Plan plan;
        private int age;

        private Flare(ItemDisplay display, Plan plan) {
            this.display = display;
            this.plan = plan;
        }

        /**
         * True once it is over and removed. The first move waits a tick: a transformation sent along with the spawn
         * would be the display's starting pose and show no interpolation.
         */
        private boolean advance() {
            age++;
            if (age >= plan.ticks() + plan.fadeTicks() || !display.isValid()
                    || plan.anchor() != null && plan.anchor().isPassable()) {
                display.remove();
                return true;
            }
            if (age == FIRST_MOVE_AGE && plan.changes()) {
                animate(plan.at(plan.to()), plan.ticks() - FIRST_MOVE_AGE);
                if (plan.move().lengthSquared() > 0) {
                    display.teleport(display.getLocation().add(plan.move()));
                }
            }
            if (plan.fadeTicks() > 0 && age == plan.ticks()) {
                animate(plan.at(new Vector3f(GONE)), plan.fadeTicks());
            }
            return false;
        }

        private void animate(Transformation to, int ticks) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(to);
        }
    }

    private final LiveCap<Flare> live;

    public Flares(int limit) {
        live = new LiveCap<>(limit);
    }

    /** One still frame, as long as the cap has room. */
    public void flash(String model, Location at, float scale, int ticks) {
        if (live.hasRoom()) {
            show(at, new Plan(Displays.model(model), UNTURNED, new Vector3f(scale), new Vector3f(scale), new Vector(), ticks, 0,
                    Display.Billboard.CENTER, true, null));
        }
    }

    public void burst(String model, Location at, float from, float to, int ticks) {
        show(at, new Plan(Displays.model(model), UNTURNED, new Vector3f(from), new Vector3f(to), new Vector(), ticks, 0,
                Display.Billboard.CENTER, true, null));
    }

    public void puff(String model, Location at, float from, float to, double rise, int ticks, int fadeTicks) {
        show(at, new Plan(Displays.model(model), UNTURNED, new Vector3f(from), new Vector3f(to), new Vector(0, rise, 0), ticks, fadeTicks,
                Display.Billboard.CENTER, true, null));
    }

    /** A flat ring facing along {@code normal} that widens to {@code diameter} while it travels; {@code tint} is its first custom_model_data colour. */
    public void ring(String model, Location at, Vector normal, Vector travel, float diameter, int ticks, Color tint) {
        show(at, new Plan(tinted(model, tint), facing(normal), new Vector3f(diameter * 0.05f), new Vector3f(diameter), travel, ticks, 0,
                Display.Billboard.FIXED, true, null));
    }

    /** {@code look} stretched from one point to another, whose length runs along its Z axis, thinning to nothing. */
    public void beam(ItemStack look, Location from, Location to, float width, float stretch, int ticks) {
        if (live.hasRoom()) {
            var line = to.toVector().subtract(from.toVector());
            var middle = from.clone().add(line.clone().multiply(0.5)).setDirection(line);
            show(middle, new Plan(look, UNTURNED, new Vector3f(width, width, stretch), new Vector3f(GONE, GONE, stretch),
                    new Vector(), ticks, 0, Display.Billboard.FIXED, true, null, new Quaternionf().rotationY((float) Math.PI)));
        }
    }

    boolean hasRoom() {
        return live.hasRoom();
    }

    void crescent(String model, Location at, Quaternionf turn, Vector3f scale, Color tint, int ticks) {
        if (live.hasRoom()) {
            show(at, new Plan(tinted(model, tint), turn, scale, scale, new Vector(), ticks, 0,
                    Display.Billboard.FIXED, true, null));
        }
    }

    /** A decal lit by its surroundings, lying on a face, gone with {@code anchor} and shrinking away at the end. */
    public void mark(String model, Location at, Vector normal, float scale, int ticks, int fadeTicks, Block anchor) {
        mark(model, at, facing(normal).rotateZ((float) ThreadLocalRandom.current().nextDouble(Math.PI * 2)), scale, ticks, fadeTicks, anchor);
    }

    /** {@link #mark} at a turn of the caller's, as {@link #lying} gives. */
    public void mark(String model, Location at, Quaternionf turn, float scale, int ticks, int fadeTicks, Block anchor) {
        show(at, new Plan(Displays.model(model), turn, new Vector3f(scale), new Vector3f(scale), new Vector(), ticks, fadeTicks,
                Display.Billboard.FIXED, false, anchor));
    }

    /** A glowing decal on a face that thins to nothing over {@code ticks} while its length shrinks less, like a cut going cold from the edges. */
    public void cooling(String model, Color tint, Location at, Quaternionf turn, float scale, int ticks, Block anchor) {
        show(at, new Plan(tinted(model, tint), turn, new Vector3f(scale), new Vector3f(scale * LENGTH_LEFT_COOLED, GONE, scale), new Vector(), ticks, 0,
                Display.Billboard.FIXED, true, anchor));
    }

    private static ItemStack tinted(String model, Color tint) {
        var look = Displays.model(model);
        look.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(tint).build());
        return look;
    }

    /**
     * The turn that lays a flat model on the face with outward {@code normal} and its length along what {@code along}
     * leaves on that face, then {@code tilt} radians further. Aimed straight at the face, {@code along} leaves no
     * direction, so only the tilt decides.
     */
    static Quaternionf lying(Vector normal, Vector along, double tilt) {
        var face = normal.toVector3f().normalize();
        var turn = facing(normal);
        var length = turn.transform(new Vector3f(FLAT_MODEL_LENGTH));
        var wanted = along.toVector3f();
        wanted.fma(-face.dot(wanted), face);
        double toWanted = wanted.lengthSquared() < HEAD_ON ? 0
                : Math.atan2(face.dot(new Vector3f(length).cross(wanted)), length.dot(wanted));
        return turn.rotateZ((float) (toWanted + tilt));
    }

    private static Quaternionf facing(Vector normal) {
        return new Quaternionf().rotationTo(FLAT_MODEL_NORMAL,
                new Vector3f((float) normal.getX(), (float) normal.getY(), (float) normal.getZ()).normalize());
    }

    private void show(Location at, Plan plan) {
        var spot = plan.turn() == UNTURNED ? at.clone() : at.clone().setRotation(0, 0);
        var display = Displays.item(spot, plan.look(), spawned -> {
            spawned.setBillboard(plan.billboard());
            spawned.setTransformation(plan.at(plan.from()));
            spawned.setTeleportDuration(Math.min(LONGEST_GLIDE, plan.ticks()));
            if (plan.lit()) {
                spawned.setBrightness(Displays.LIT);
            }
        });
        live.admit(new Flare(display, plan)).ifPresent(oldest -> oldest.display.remove());
    }

    public void tick() {
        live.removeIf(Flare::advance);
    }

    public void clear() {
        live.drain(flare -> flare.display.remove());
    }
}
