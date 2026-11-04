package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.item.Items;
import me.blockcraft.sternenfront.bot.BotSnapshot.Point;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class DroidRig implements AutoCloseable {

    private static final float SEATED_LEG = -1.35f;
    private static final float SEATED_DROP = 0.2f;

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
    private final Interaction target;
    private final TextDisplay name;
    private ItemStack held = ItemStack.empty();
    private Location previous;
    private double stride;
    private final RigBody body;
    private final Predicate<Player> fighting;
    private final Supplier<Vector> aim;
    private final float turnDegrees;
    private float bodyYaw;

    DroidRig(Plugin plugin, LivingEntity actor, RigBody body, Predicate<Player> fighting, Supplier<Vector> aim, float turnDegrees) {
        this.fighting = fighting;
        this.plugin = plugin;
        this.actor = actor;
        this.body = body;
        this.aim = aim;
        this.turnDegrees = turnDegrees;
        previous = actor.getLocation();
        bodyYaw = (float) Math.toRadians(-previous.clone().setDirection(aim.get()).getYaw());
        torso = part("torso");
        head = part("head");
        leftArm = part(body.leftArm());
        rightArm = part("arm");
        leftLeg = part("leg");
        rightLeg = part("leg");
        weapon = spawn(ItemDisplay.class);
        weapon.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        target = spawn(Interaction.class);
        target.setResponsive(false);
        name = spawn(TextDisplay.class);
        name.setBillboard(Display.Billboard.CENTER);
        name.setShadowed(true);
        name.setSeeThrough(false);
        var label = actor instanceof Player player ? player.displayName() : actor.customName();
        name.text((label == null ? Component.text(actor.getName()) : label).color(body.faction().text()));
    }

    LivingEntity actor() {
        return actor;
    }

    UUID targetId() {
        return target.getUniqueId();
    }

    boolean owns(Entity entity) {
        return entities.contains(entity);
    }

    RigSnapshot snapshot() {
        var parts = new ArrayList<RigSnapshot.Part>();
        var names = List.of("torso", "head", "left-arm", "right-arm", "left-leg", "right-leg", "weapon");
        var displays = List.of(torso, head, leftArm, rightArm, leftLeg, rightLeg, weapon);
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
        return new RigSnapshot(body.family(), Point.of(previous), stride, List.copyOf(viewers), List.copyOf(parts));
    }

    boolean shownTo(Player player) {
        return viewers.contains(player.getUniqueId());
    }

    private ItemDisplay part(String part) {
        var display = spawn(ItemDisplay.class);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setItemStack(Items.of(Material.PAPER).model(Key.key("blockcraft", "sternenfront/body/"
                + body.family() + "/" + part)).build());
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

    void tick() {
        tick(0);
    }

    void tick(double recoil) {
        var at = actor.getLocation();
        double moved = Math.hypot(at.getX() - previous.getX(), at.getZ() - previous.getZ());
        stride += Math.min(moved, 0.5) * 7;
        boolean seated = actor.isInsideVehicle();
        float step = actor.isOnGround() ? (float) (Math.sin(stride) * Math.min(moved * 3, 0.65)) : 0.2f;
        float leftStride = seated ? SEATED_LEG : step;
        float rightStride = seated ? SEATED_LEG : -step;
        boolean crouching = actor instanceof Player player && player.isSneaking();
        float drop = seated ? SEATED_DROP : crouching ? 0.28f : 0;
        var looking = at.clone().setDirection(aim.get());
        float facing = (float) Math.toRadians(-looking.getYaw());
        bodyYaw = actor instanceof Player ? (float) Math.toRadians(-actor.getBodyYaw()) : RigPose.turn(bodyYaw, facing, turnDegrees);
        float yaw = bodyYaw;
        float pitch = (float) Math.toRadians(Math.clamp(looking.getPitch(), -65, 65));
        at.setYaw(0);
        at.setPitch(0);
        var joints = body.joints();
        float scale = body.scale();
        pose(torso, at, yaw, yaw, 0, joints.torsoY() * scale - drop, 0, 0, joints.torsoLean() + (crouching ? 0.15f : 0), scale);
        pose(head, at, yaw, facing, 0, joints.headY() * scale - drop, joints.headZ() * scale, 0, pitch, scale);
        pose(leftLeg, at, yaw, yaw, -joints.hipX() * scale, joints.hipY() * scale - (seated ? drop : drop / 2), 0, 0, leftStride, scale);
        pose(rightLeg, at, yaw, yaw, joints.hipX() * scale, joints.hipY() * scale - (seated ? drop : drop / 2), 0, 0, rightStride, scale);
        float shoulderX = joints.shoulderX() * scale;
        float shoulderY = joints.shoulderY() * scale - drop;
        float shoulderZ = joints.shoulderZ() * scale;
        pose(leftArm, at, yaw, facing - body.gripTurn(), shoulderX, shoulderY, shoulderZ, 0, pitch + body.armLift() + 0.05f, scale);
        pose(rightArm, at, yaw, facing, -shoulderX, shoulderY, shoulderZ, 0, pitch + body.armLift(), scale);
        pose(weapon, at, yaw, facing, joints.weaponX() * scale, joints.weaponY() * scale - drop, 0,
                joints.weaponZ() * scale - (float) recoil * .06f, pitch, 0.65f);
        var equipment = actor.getEquipment();
        var current = equipment == null || !body.showsWeapon() ? ItemStack.empty() : equipment.getItemInMainHand();
        if (!current.equals(held)) {
            held = current.clone();
            weapon.setItemStack(held);
        }
        target.setInteractionWidth((float) actor.getWidth());
        target.setInteractionHeight((float) actor.getHeight());
        place(target, at);
        place(name, at.clone().add(0, actor.getHeight() + 0.3, 0));
        previous = at;
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    private void pose(ItemDisplay display, Location at, float anchorYaw, float yaw, float x, float y, float bodyZ, float aimZ, float pitch, float scale) {
        place(display, at);
        var pose = RigPose.at(anchorYaw, yaw, x, y, bodyZ, aimZ, pitch, scale);
        if (!pose.equals(poses.get(display))) {
            display.setInterpolationDelay(0);
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
        if (viewers.remove(player.getUniqueId())) {
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
