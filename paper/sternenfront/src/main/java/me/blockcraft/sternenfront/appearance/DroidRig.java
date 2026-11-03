package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class DroidRig implements AutoCloseable {

    private final Plugin plugin;
    private final LivingEntity actor;
    private final List<Entity> entities = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();
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
    private final boolean droid;
    private final Supplier<Vector> aim;

    DroidRig(Plugin plugin, LivingEntity actor, boolean droid, Supplier<Vector> aim) {
        this.plugin = plugin;
        this.actor = actor;
        this.droid = droid;
        this.aim = aim;
        previous = actor.getLocation();
        torso = part("torso");
        head = part("head");
        leftArm = part("arm");
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
        name.text((label == null ? Component.text(actor.getName()) : label).color(droid ? NamedTextColor.AQUA : NamedTextColor.GOLD));
    }

    LivingEntity actor() {
        return actor;
    }

    UUID targetId() {
        return target.getUniqueId();
    }

    boolean shownTo(Player player) {
        return viewers.contains(player.getUniqueId());
    }

    private ItemDisplay part(String part) {
        var display = spawn(ItemDisplay.class);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setItemStack(Items.of(Material.PAPER).model(Key.key("blockcraft", "sternenfront/"
                + (droid ? "droid/" : "infantry/") + part)).build());
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
        var at = actor.getLocation();
        double moved = Math.hypot(at.getX() - previous.getX(), at.getZ() - previous.getZ());
        stride += Math.min(moved, 0.5) * 7;
        float step = actor.isOnGround() ? (float) (Math.sin(stride) * Math.min(moved * 3, 0.65)) : 0.2f;
        boolean crouching = actor instanceof Player player && player.isSneaking();
        float drop = crouching ? 0.28f : 0;
        var looking = at.clone().setDirection(aim.get());
        float facing = (float) Math.toRadians(-looking.getYaw());
        float yaw = actor instanceof Player ? (float) Math.toRadians(-actor.getBodyYaw()) : facing;
        float pitch = (float) Math.toRadians(Math.clamp(looking.getPitch(), -65, 65));
        at.setYaw(0);
        at.setPitch(0);
        pose(torso, at, yaw, 0, 1 - drop, 0, crouching ? 0.15f : 0, 1);
        pose(head, at, facing, 0, 1.48f - drop, 0, pitch, 1);
        pose(leftLeg, at, yaw, -0.13f, 0.95f - drop / 2, 0, step, 1);
        pose(rightLeg, at, yaw, 0.13f, 0.95f - drop / 2, 0, -step, 1);
        float shoulder = droid ? 0.23f : 0.35f;
        pose(leftArm, at, facing, shoulder, 1.36f - drop, 0, pitch - 1.05f, 1);
        pose(rightArm, at, facing, -shoulder, 1.36f - drop, 0, pitch - 1.1f, 1);
        pose(weapon, at, facing, -shoulder + 0.03f, 1.27f - drop, 0.53f, pitch, 0.65f);
        var equipment = actor.getEquipment();
        var current = equipment == null ? ItemStack.empty() : equipment.getItemInMainHand();
        if (!current.equals(held)) {
            held = current.clone();
            weapon.setItemStack(held);
        }
        target.setInteractionWidth((float) actor.getWidth());
        target.setInteractionHeight((float) actor.getHeight());
        target.teleport(at);
        name.teleport(at.clone().add(0, actor.getHeight() + 0.3, 0));
        previous = at;
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    private static void pose(ItemDisplay display, Location at, float yaw, float x, float y, float z, float pitch, float scale) {
        display.teleport(at);
        display.setInterpolationDelay(0);
        display.setTransformation(RigPose.at(yaw, x, y, z, pitch, scale));
    }

    void refresh(Player player) {
        boolean show = !player.equals(actor) && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().equals(actor.getWorld()) && BlockcraftApi.get().resourcePack().applied(player);
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
