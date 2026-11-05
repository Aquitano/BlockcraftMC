package me.blockcraft.api.presentation;

import me.blockcraft.api.dialogue.Dialogue;
import me.blockcraft.api.dialogue.Dialogues;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Consumer;

public final class Discoveries {
    private final Plugin plugin;
    private final Dialogues dialogues;
    private final ResourcePacks packs;
    private final Messages messages;
    private final Dialogue.Loaded demo;
    private final double distance;
    private final double maxDistance;
    private final int highlightTicks;
    private final double radius;

    public Discoveries(Plugin plugin, Dialogues dialogues, ResourcePacks packs, Messages messages, Map<String, Object> config) {
        this.plugin = plugin;
        this.dialogues = dialogues;
        this.packs = packs;
        this.messages = messages;
        demo = Dialogue.load(YamlFile.section(config, "demo"));
        distance = bounded(config, "distance", 4, 2, 8);
        maxDistance = bounded(config, "max-distance", 16, 4, 32);
        highlightTicks = (int) bounded(config, "highlight-ticks", 40, 1, 200);
        radius = bounded(config, "highlight-radius", 0.4, 0.1, 2);
    }

    public boolean start(Player player, LivingEntity actor, Location target, Dialogue dialogue, Messages text,
                         PresentationScope scope, Consumer<String> completed) {
        try {
            if (scope.closed() || !player.isOnline() || player.isDead() || !packs.applied(player) || !actor.isValid()
                    || actor instanceof Player || !player.getWorld().equals(actor.getWorld())
                    || !player.getWorld().equals(target.getWorld())) {
                scope.close();
                return false;
            }
            var focus = target.clone();
            if (!dialogues.start(player, dialogue, text, scope, completed)) return false;
            if (!actor.isValid()) {
                scope.close();
                return false;
            }
            var original = actor.getLocation();
            var look = focus.toVector().subtract(actor.getEyeLocation().toVector());
            if (look.lengthSquared() > 0.0001) {
                var facing = actor.getLocation().setDirection(look);
                actor.setRotation(facing.getYaw(), facing.getPitch());
                scope.own(() -> {
                    if (actor.isValid()) actor.setRotation(original.getYaw(), original.getPitch());
                });
            }
            player.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.3f);
            scope.own(() -> player.stopSound(Sound.BLOCK_AMETHYST_BLOCK_CHIME));
            var age = new int[1];
            var task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (scope.closed()) return;
                if (!player.isOnline() || player.isDead() || !actor.isValid() || !player.getWorld().equals(focus.getWorld())
                        || player.getLocation().distanceSquared(focus) > maxDistance * maxDistance) {
                    scope.close();
                    return;
                }
                if (age[0]++ >= highlightTicks) return;
                double angle = age[0] * Math.PI / 10;
                player.spawnParticle(Particle.END_ROD, focus.clone().add(Math.cos(angle) * radius,
                        Math.sin(angle * 2) * radius / 2, Math.sin(angle) * radius), 1, 0, 0, 0, 0);
            }, 1, 1);
            scope.own(task::cancel);
            return true;
        } catch (RuntimeException failure) {
            scope.close();
            plugin.getSLF4JLogger().warn("Could not start discovery", failure);
            return false;
        }
    }

    public boolean startDemo(Player player) {
        if (!demo.valid()) {
            player.sendMessage(messages.get("dialogue.invalid"));
            return false;
        }
        if (!packs.applied(player)) {
            player.sendMessage(messages.get("presentation.pack-required"));
            return false;
        }
        if (dialogues.active(player)) {
            player.sendMessage(messages.get("presentation.busy"));
            return false;
        }
        var forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.0001) forward.setZ(1);
        forward.normalize();
        var center = player.getLocation().add(forward.clone().multiply(distance));
        var right = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX());
        var actorAt = center.clone().subtract(right.clone().multiply(1.2));
        var target = center.clone().add(right.clone().multiply(0.7)).add(0, 1.2, 0);
        var scope = new PresentationScope(plugin);
        try {
            var actor = actorAt.getWorld().spawn(actorAt, Villager.class, npc -> {
                npc.setVisibleByDefault(false);
                npc.setPersistent(false);
                npc.setAI(false);
                npc.setGravity(false);
                npc.setSilent(true);
                npc.setInvulnerable(true);
                npc.setCollidable(false);
                npc.setProfession(Villager.Profession.LIBRARIAN);
                npc.customName(messages.get("presentation.discovery.guide"));
                npc.setCustomNameVisible(true);
            });
            scope.own(actor::remove);
            player.showEntity(plugin, actor);
            var artifact = target.getWorld().spawn(target, ItemDisplay.class, display -> {
                display.setVisibleByDefault(false);
                display.setPersistent(false);
                display.setShadowRadius(0);
                display.setItemStack(VisualEffects.model("presentation/crystal", VisualEffects.MaterialEffect.PULSE));
            });
            scope.own(artifact::remove);
            player.showEntity(plugin, artifact);
            boolean started = start(player, actor, target, demo.dialogue(), messages, scope,
                    ending -> player.sendMessage(messages.get("presentation.discovery.completed")));
            if (started) player.sendMessage(messages.get("presentation.discovery.started"));
            return started;
        } catch (RuntimeException failure) {
            scope.close();
            plugin.getSLF4JLogger().warn("Could not create discovery demo", failure);
            return false;
        }
    }

    private static double bounded(Map<String, Object> config, String key, double fallback, double min, double max) {
        double value = config.get(key) instanceof Number number ? number.doubleValue() : fallback;
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
