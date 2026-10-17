package me.blockcraft.lobby.listener;

import me.blockcraft.api.world.BuildMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Set;

public final class ProtectionListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> BLOCKED_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.DROWNING);
    private static final Set<Material> LOCKED_BLOCKS = Set.of(
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.CRAFTING_TABLE, Material.FURNACE, Material.LEVER, Material.CHEST,
            Material.CHISELED_BOOKSHELF, Material.DECORATED_POT, Material.JUKEBOX);
    private static final Set<CreatureSpawnEvent.SpawnReason> MANUAL_SPAWNS = Set.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM, CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

    private final BuildMode buildMode;

    public ProtectionListener(BuildMode buildMode) {
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        event.setCancelled(!MANUAL_SPAWNS.contains(event.getSpawnReason()));
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
            var world = event.getWorld();
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && BLOCKED_CAUSES.contains(event.getCause())) {
            player.setFireTicks(0);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        boolean protectedAnimal = victim instanceof Player || victim instanceof Blaze || victim instanceof Rabbit
                || victim instanceof Wolf || victim instanceof Chicken || victim instanceof Pig;
        boolean decoration = victim instanceof Hanging || victim instanceof ArmorStand;
        if (protectedAnimal || (victim instanceof Villager && event.getDamager() instanceof Player)
                || (decoration && !byBuilder(event.getDamageSource()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        boolean byBuilder = event instanceof HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player && buildMode.isEnabled(player);
        event.setCancelled(!byBuilder);
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame && !buildMode.isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || buildMode.isEnabled(event.getPlayer())) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (LOCKED_BLOCKS.contains(type) || Tag.FLOWER_POTS.isTagged(type) || Tag.WOODEN_TRAPDOORS.isTagged(type)
                || Tag.WOODEN_BUTTONS.isTagged(type) || Tag.WOODEN_DOORS.isTagged(type)) {
            event.setCancelled(true);
        }
    }

    private boolean byBuilder(DamageSource source) {
        return source.getCausingEntity() instanceof Player player && buildMode.isEnabled(player);
    }
}
