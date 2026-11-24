package me.blockcraft.sternenfront.appearance;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.sternenfront.bot.BotSnapshot.Point;
import me.blockcraft.sternenfront.combat.Stance;
import me.blockcraft.sternenfront.game.Faction;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class BodyRig implements AutoCloseable {

    private static final float SEATED_LEG = -1.35f;
    private static final float SEATED_DROP = 0.2f;
    private static final float CROUCH_DROP = 0.28f;
    private static final float CROUCH_LEAN = 0.15f;
    private static final float AIRBORNE_STEP = 0.2f;
    private static final float MAX_PITCH_DEGREES = 65;
    /** A hero has no appearance.motion profile: its walk cycle advances with distance and its legs swing with speed. */
    private static final double HERO_STRIDE_CAP_BLOCKS = 0.5;
    private static final double HERO_STRIDE_PER_BLOCK = 7;
    private static final double HERO_STEP_PER_BLOCK = 3;
    private static final double HERO_MAX_STEP = 0.65;
    private static final double HERO_RECOIL_BLOCKS = 0.06;
    private static final float NAME_SCALE = 0.6f;
    private static final float NAME_VIEW_RANGE = 0.4f;
    private static final double NAME_ABOVE_HEAD = 0.3;
    /** As long as vanilla's hurt flash and BetterModel's damage tint. */
    static final int HURT_TICKS = 10;

    private final Plugin plugin;
    private final LivingEntity actor;
    private final List<Entity> entities = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<ItemDisplay, Transformation> poses = new HashMap<>();
    private final ItemDisplay torso;
    private final ItemDisplay head;
    private final ItemDisplay leftArm;
    private final ItemDisplay rightArm;
    private final ItemDisplay leftLeg;
    private final ItemDisplay rightLeg;
    private final ItemDisplay weapon;
    private final Optional<ItemDisplay> offhand;
    private final Interaction hitBox;
    private final TextDisplay name;
    private ItemStack held = ItemStack.empty();
    private ItemStack heldOffhand = ItemStack.empty();
    private Location previous;
    private double stride;
    private final RigBody kind;
    private final Predicate<Player> fighting;
    private final Supplier<Vector> aim;
    private final float turnDegrees;
    private float bodyYaw;
    private final Optional<RigMotion> motion;
    private int recoilLeft;
    private int hurtLeft;
    private Insignia insignia;
    private final Supplier<Optional<Stance>> stance;
    private HeroStances.Limbs limbs;
    private int sinceStance;

    BodyRig(Plugin plugin, LivingEntity actor, RigBody kind, Predicate<Player> fighting, Supplier<Vector> aim, float turnDegrees,
            Optional<RigMotion> motion, Insignia insignia,
            Supplier<Optional<Stance>> stance) {
        this.fighting = fighting;
        this.stance = stance;
        this.plugin = plugin;
        this.actor = actor;
        this.kind = kind;
        this.aim = aim;
        this.turnDegrees = turnDegrees;
        this.motion = motion;
        this.insignia = insignia;
        previous = actor.getLocation();
        bodyYaw = (float) Math.toRadians(-previous.clone().setDirection(aim.get()).getYaw());
        torso = spawnPart("torso");
        head = spawnPart("head");
        leftArm = spawnPart(kind.leftArmPart());
        rightArm = spawnPart("arm");
        leftLeg = spawnPart("leg");
        rightLeg = spawnPart("leg_right");
        paint(Color.WHITE, Color.fromRGB(insignia.colour()));
        weapon = spawn(ItemDisplay.class);
        weapon.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        offhand = kind.dualWield() ? Optional.of(spawn(ItemDisplay.class)) : Optional.empty();
        offhand.ifPresent(display -> display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE));
        hitBox = spawn(Interaction.class);
        hitBox.setResponsive(false);
        name = spawn(TextDisplay.class);
        name.setBillboard(Display.Billboard.CENTER);
        name.setShadowed(true);
        name.setSeeThrough(false);
        name.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        name.setViewRange(NAME_VIEW_RANGE);
        name.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(NAME_SCALE), new Quaternionf()));
        var label = actor instanceof Player player ? player.displayName() : actor.customName();
        name.text((label == null ? Component.text(actor.getName()) : label).color(kind.faction().text()));
    }

    LivingEntity actor() {
        return actor;
    }

    RigBody kind() {
        return kind;
    }

    UUID hitBoxId() {
        return hitBox.getUniqueId();
    }

    boolean owns(Entity entity) {
        return entities.contains(entity);
    }

    RigSnapshot snapshot() {
        var parts = new ArrayList<RigSnapshot.Part>();
        var names = new ArrayList<>(List.of("torso", "head", "left-arm", "right-arm", "left-leg", "right-leg", "weapon"));
        var displays = new ArrayList<>(List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg, weapon));
        offhand.ifPresent(display -> {
            names.add("offhand");
            displays.add(display);
        });
        for (int index = 0; index < displays.size(); index++) {
            var display = displays.get(index);
            var at = display.getLocation();
            var transform = display.getTransformation();
            var translation = transform.getTranslation();
            var left = transform.getLeftRotation();
            var right = transform.getRightRotation();
            var scale = transform.getScale();
            parts.add(new RigSnapshot.Part(names.get(index), display.getUniqueId(), Point.of(at), at.getYaw(), at.getPitch(),
                    List.of(translation.x, translation.y, translation.z), List.of(left.x, left.y, left.z, left.w),
                    List.of(right.x, right.y, right.z, right.w), List.of(scale.x, scale.y, scale.z),
                    display.getTeleportDuration(), display.getInterpolationDuration(), display.getInterpolationDelay()));
        }
        return new RigSnapshot(kind.family(), Point.of(previous), stride, List.copyOf(viewers), List.copyOf(parts));
    }

    ItemDisplay torso() {
        return torso;
    }

    boolean shownTo(Player player) {
        return viewers.contains(player.getUniqueId());
    }

    List<Entity> outlineParts() {
        return List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg, weapon);
    }

    void hurt(Color tint) {
        if (hurtLeft == 0) {
            paint(tint, tint);
        }
        hurtLeft = HURT_TICKS;
    }

    /** New insignia wait for the hurt flash to end, which paints them anyway. */
    void wear(Insignia next) {
        if (!next.equals(insignia)) {
            insignia = next;
            if (hurtLeft == 0) {
                paint(Color.WHITE, Color.fromRGB(insignia.colour()));
            }
        }
    }

    private void paint(Color plates, Color marks) {
        var data = CustomModelData.customModelData().addColor(plates).addColor(marks).addFlag(insignia.leader()).build();
        for (var part : List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg)) {
            var look = part.getItemStack();
            look.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data);
            part.setItemStack(look);
        }
    }

    private ItemDisplay spawnPart(String part) {
        var display = spawn(ItemDisplay.class);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setItemStack(Displays.model("sternenfront/body/" + kind.family() + "/" + part));
        return display;
    }

    private <T extends Entity> T spawn(Class<T> type) {
        var entity = actor.getWorld().spawn(previous, type, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            if (spawned instanceof Display display) {
                display.setTeleportDuration(1);
                display.setInterpolationDuration(1);
                display.setDisplayWidth(2);
                display.setDisplayHeight(3);
            }
        });
        entities.add(entity);
        return entity;
    }

    /** {@code fired} starts a soldier's kick; a hero's follows the gun's {@code recoil} as it eases off. */
    void tick(boolean fired, double recoil) {
        var at = actor.getLocation();
        double moved = Math.hypot(at.getX() - previous.getX(), at.getZ() - previous.getZ());
        stride += motion.map(profile -> profile.advance(moved)).orElseGet(() -> Math.min(moved, HERO_STRIDE_CAP_BLOCKS) * HERO_STRIDE_PER_BLOCK);
        var movement = motion.map(profile -> profile.pose(stride,
                actor.isOnGround() && !actor.isInsideVehicle() ? moved : 0))
                .orElseGet(() -> new RigMotion.Pose((float) (Math.sin(stride) * Math.min(moved * HERO_STEP_PER_BLOCK, HERO_MAX_STEP)), 0, 0, 0));
        if (fired) recoilLeft = motion.map(RigMotion::recoilTicks).orElse(0);
        double kick = motion.map(profile -> (double) recoilLeft / profile.recoilTicks()).orElse(recoil);
        float recoilPitch = (float) -Math.toRadians(motion.map(RigMotion::recoilDegrees).orElse(0.0) * kick);
        float recoilDistance = (float) (motion.map(RigMotion::recoilBlocks).orElse(HERO_RECOIL_BLOCKS) * kick);
        recoilLeft = Math.max(0, recoilLeft - 1);
        if (hurtLeft > 0 && --hurtLeft == 0) {
            paint(Color.WHITE, Color.fromRGB(insignia.colour()));
        }
        boolean seated = actor.isInsideVehicle() && kind.faction() == Faction.KOLONIEN;
        float step = actor.isInsideVehicle() ? 0 : actor.isOnGround() ? movement.step() : AIRBORNE_STEP;
        float leftStride = seated ? SEATED_LEG : step;
        float rightStride = seated ? SEATED_LEG : -step;
        boolean crouching = actor instanceof Player player && player.isSneaking();
        float drop = seated ? SEATED_DROP : crouching ? CROUCH_DROP : 0;
        var looking = at.clone().setDirection(aim.get());
        float facing = (float) Math.toRadians(-looking.getYaw());
        bodyYaw = actor instanceof Player ? (float) Math.toRadians(-actor.getBodyYaw()) : RigPose.turn(bodyYaw, facing, turnDegrees);
        float yaw = bodyYaw;
        float pitch = (float) Math.toRadians(Math.clamp(looking.getPitch(), -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES));
        at.setYaw(0);
        at.setPitch(0);
        var joints = kind.joints();
        float scale = kind.scale();
        pose(torso, at, yaw, yaw + movement.sway(), 0, joints.torsoY() * scale - drop + movement.bob(), 0, 0,
                joints.torsoLean() + movement.lean() + recoilPitch / 3 + (crouching ? CROUCH_LEAN : 0), scale);
        pose(head, at, yaw, facing, 0, joints.headY() * scale - drop, joints.headZ() * scale, 0, pitch, scale);
        pose(leftLeg, at, yaw, yaw, joints.hipX() * scale, joints.hipY() * scale - (seated ? drop : drop / 2), 0, 0, leftStride, scale);
        pose(rightLeg, at, yaw, yaw, -joints.hipX() * scale, joints.hipY() * scale - (seated ? drop : drop / 2), 0, 0, rightStride, scale);
        float shoulderX = joints.shoulderX() * scale;
        float shoulderY = joints.shoulderY() * scale - drop;
        float shoulderZ = joints.shoulderZ() * scale;
        var wanted = HeroStances.of(stance.get(), kind.armLift());
        sinceStance = wanted.equals(limbs) ? sinceStance + 1 : 0;
        limbs = wanted;
        int blendTicks = Math.max(1, wanted.blendTicks() - sinceStance);
        pose(leftArm, at, yaw, facing - kind.gripTurn() + wanted.leftTurn(), shoulderX, shoulderY, shoulderZ, 0,
                pitch + wanted.leftLift() + RigBody.LEFT_ARM_DROP + recoilPitch, 0, scale, blendTicks);
        pose(rightArm, at, yaw, facing + wanted.rightTurn(), -shoulderX, shoulderY, shoulderZ, 0,
                pitch + wanted.rightLift() + recoilPitch, 0, scale, blendTicks);
        pose(weapon, at, yaw, facing + wanted.bladeTurn(), joints.weaponX() * scale + wanted.bladeX(),
                joints.weaponY() * scale - drop + wanted.bladeY(), 0, joints.weaponZ() * scale - recoilDistance,
                pitch + recoilPitch + wanted.bladeTilt(), wanted.bladeRoll(), RigBody.WEAPON_SCALE, blendTicks);
        offhand.ifPresent(display -> pose(display, at, yaw, facing, -joints.weaponX() * scale, joints.weaponY() * scale - drop, 0,
                joints.weaponZ() * scale - recoilDistance, pitch + recoilPitch, RigBody.WEAPON_SCALE));
        var equipment = actor.getEquipment();
        var current = equipment == null || !kind.showsWeapon() ? ItemStack.empty() : equipment.getItemInMainHand();
        if (!current.equals(held)) {
            held = current.clone();
            weapon.setItemStack(held);
        }
        offhand.ifPresent(display -> {
            var currentOffhand = equipment == null ? ItemStack.empty() : equipment.getItemInOffHand();
            if (!currentOffhand.equals(heldOffhand)) {
                heldOffhand = currentOffhand.clone();
                display.setItemStack(heldOffhand);
            }
        });
        hitBox.setInteractionWidth((float) actor.getWidth());
        hitBox.setInteractionHeight((float) actor.getHeight());
        place(hitBox, at);
        place(name, at.clone().add(0, actor.getHeight() + NAME_ABOVE_HEAD, 0));
        previous = at;
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    private void pose(ItemDisplay display, Location at, float anchorYaw, float yaw, float x, float y, float bodyZ, float aimZ, float pitch, float scale) {
        pose(display, at, anchorYaw, yaw, x, y, bodyZ, aimZ, pitch, 0, scale, 1);
    }

    /** Moves the part into its pose over {@code ticks}, so a stance eases in rather than snapping. */
    private void pose(ItemDisplay display, Location at, float anchorYaw, float yaw, float x, float y, float bodyZ, float aimZ,
                      float pitch, float roll, float scale, int ticks) {
        place(display, at);
        var pose = RigPose.at(anchorYaw, yaw, x, y, bodyZ, aimZ, pitch, roll, scale);
        if (!pose.equals(poses.get(display))) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(pose);
            poses.put(display, pose);
        }
    }

    private static void place(Entity entity, Location at) {
        if (!entity.getLocation().equals(at)) {
            entity.teleport(at);
        }
    }

    void refresh(Player player) {
        boolean show = !player.equals(actor) && player.getGameMode() != GameMode.SPECTATOR && fighting.test(player)
                && player.getWorld().equals(actor.getWorld());
        if (show && !viewers.contains(player.getUniqueId()) && player.canSee(actor)) {
            player.hideEntity(plugin, actor);
            entities.forEach(entity -> player.showEntity(plugin, entity));
            viewers.add(player.getUniqueId());
        } else if (!show) {
            forget(player);
        }
    }

    void forget(Player player) {
        // Bukkit refuses both calls while the plugin shuts down, and the entities go with it anyway.
        if (viewers.remove(player.getUniqueId()) && plugin.isEnabled()) {
            entities.forEach(entity -> player.hideEntity(plugin, entity));
            player.showEntity(plugin, actor);
        }
    }

    @Override
    public void close() {
        plugin.getServer().getOnlinePlayers().forEach(this::forget);
        entities.forEach(Entity::remove);
    }
}
