package me.blockcraft.lobby.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.world.MapSaveEvent;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.activity.Parkour;
import me.blockcraft.lobby.gadget.Gadget;
import me.blockcraft.lobby.gadget.Gadgets;
import me.blockcraft.lobby.item.LobbyItem;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;



public final class GadgetListener implements Listener {

    private static final NamespacedKey PAINT_KEY = new NamespacedKey("lobby", "paint");
    private static final double HOOK_PULL = 1.6;
    private static final double HOOK_LIFT = 0.4;
    private static final double TNT_LIFT = 1.6;
    private static final double SNOWBALL_SPEED = 2.0;

    private final Gadgets gadgets;
    private final Parkour parkour;

    public GadgetListener(Gadgets gadgets, Parkour parkour) {
        this.gadgets = gadgets;
        this.parkour = parkour;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()
                || LobbyItem.of(event.getItem()).filter(LobbyItem.GADGET::equals).isEmpty()) {
            return;
        }
        var player = event.getPlayer();
        if (parkour.running(player)) {
            event.setCancelled(true);
            return;
        }
        Gadget.of(event.getItem()).ifPresent(gadget -> {
            switch (gadget) {
                case TNT_JUMP -> {
                    event.setCancelled(true);
                    tntJump(player);
                }
                case PAINT_GUN -> {
                    event.setCancelled(true);
                    shootPaint(player);
                }
                case GRAPPLING_HOOK -> {
                }
                case BIRTHDAY_CAKE -> {
                    event.setCancelled(true);
                    if (gadgets.tryUse(player)) {
                        Sounds.ACHIEVEMENT.play(player);
                        for (var viewer : player.getWorld().getNearbyPlayers(player.getLocation(), 24)) {
                            if (viewer.canSee(player) && BlockcraftApi.get().settings()
                                    .enabled(viewer, PlayerSetting.EFFECTS)) {
                                viewer.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.6, 0.6, 0.6, 0);
                            }
                        }
                    }
                }
            }
        });
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        var player = event.getPlayer();
        if (parkour.running(player)) {
            event.setCancelled(true);
            event.getHook().remove();
            return;
        }
        if (Gadget.of(player.getInventory().getItemInMainHand()).filter(Gadget.GRAPPLING_HOOK::equals).isEmpty()
                || LobbyItem.of(player.getInventory().getItemInMainHand()).filter(LobbyItem.GADGET::equals).isEmpty()) {
            return;
        }
        switch (event.getState()) {
            case IN_GROUND, REEL_IN, CAUGHT_ENTITY -> {
                event.setCancelled(true);
                var pull = event.getHook().getLocation().toVector().subtract(player.getLocation().toVector());
                event.getHook().remove();
                if (pull.lengthSquared() < 1.0 || !gadgets.tryUse(player)) {
                    return;
                }
                player.setVelocity(pull.normalize().multiply(HOOK_PULL).setY(Math.max(pull.getY() / 4.0, HOOK_LIFT)));
                Sounds.GRAPPLE.play(player);
            }
            case FISHING, BITE, FAILED_ATTEMPT, CAUGHT_FISH, LURED -> {
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !snowball.getPersistentDataContainer().has(PAINT_KEY, PersistentDataType.BYTE)) {
            return;
        }
        var block = event.getHitBlock();
        if (block == null && event.getHitEntity() != null) {
            block = event.getHitEntity().getLocation().getBlock().getRelative(0, -1, 0);
        }
        if (block != null) {
            gadgets.paint(block);
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.0f, 1.2f);
        }
    }

    @EventHandler
    public void onMapSave(MapSaveEvent event) {
        gadgets.restorePaint();
    }

    private void tntJump(Player player) {
        if (!gadgets.tryUse(player)) {
            return;
        }
        player.setVelocity(player.getLocation().getDirection().multiply(0.5).setY(TNT_LIFT));
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    private void shootPaint(Player player) {
        if (!gadgets.tryUse(player)) {
            return;
        }
        var snowball = player.launchProjectile(Snowball.class, player.getLocation().getDirection().multiply(SNOWBALL_SPEED));
        snowball.getPersistentDataContainer().set(PAINT_KEY, PersistentDataType.BYTE, (byte) 1);
        Sounds.PAINT_THROW.play(player);
    }
}
