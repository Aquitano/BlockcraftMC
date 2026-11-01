package me.blockcraft.api.world;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import me.blockcraft.api.player.InventoryInspection;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Keeps a world as it was built, for everyone but a player in build mode: no block answers a click or a step unless it
 * is {@code usable}, nothing is built, emptied, hung up, dressed, dropped, picked up or moved in an inventory, no boat
 * or minecart is broken, and no entity answers a right click or opens its inventory, so a listener that makes an NPC
 * answer must not ignore cancelled clicks. No projectile, mob or fire changes a block, no block breaks off what held
 * it up, nothing is set alight, grown, primed or hatched, and no mob or arrow presses a plate or button. Nobody and
 * nothing is hurt, hungry or given experience, and nothing explodes. Held items do nothing unless the world is
 * {@link #withHeldItems}. The void and /kill still kill, so whoever no {@code rescue} spawn catches does not fall
 * forever.
 */
public final class WorldProtection implements Listener {

    private static final double FALL_HEIGHT = 0.0;
    private static final Set<EntityDamageEvent.DamageCause> FATAL =
            EnumSet.of(EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.KILL);

    private final BooleanSupplier active;
    private final Optional<World> world;
    private final BuildMode buildMode;
    private final Predicate<Block> usable;
    private final Supplier<Optional<Location>> rescue;
    private final boolean heldItems;

    private WorldProtection(BooleanSupplier active, Optional<World> world, BuildMode buildMode, Predicate<Block> usable,
                            Supplier<Optional<Location>> rescue, boolean heldItems) {
        this.active = active;
        this.world = world;
        this.buildMode = buildMode;
        this.usable = usable;
        this.rescue = rescue;
        this.heldItems = heldItems;
    }

    /** Every world while {@code active} holds, such as a game's lobby phase. */
    public static WorldProtection during(BooleanSupplier active, BuildMode buildMode) {
        return new WorldProtection(active, Optional.empty(), buildMode, block -> false, Optional::empty, false);
    }

    public static WorldProtection in(World world, BuildMode buildMode) {
        return new WorldProtection(() -> true, Optional.of(world), buildMode, block -> false, Optional::empty, false);
    }

    /** The blocks that still answer a right click, such as the doors of a map people explore. */
    public WorldProtection usable(Predicate<Block> usable) {
        return new WorldProtection(active, world, buildMode, usable, rescue, heldItems);
    }

    /** Where a player who falls below the map is put back. */
    public WorldProtection rescue(Supplier<Optional<Location>> spawn) {
        return new WorldProtection(active, world, buildMode, usable, spawn, heldItems);
    }

    /**
     * Held items keep working, so rods cast and toys fly, and a click on a block only keeps the block from answering.
     * Paper reuses a block click's verdict on the item for the client's following click into the air, so refusing the
     * item there would stop every rod or toy aimed at the ground; what an item does to a block is refused on its own.
     */
    public WorldProtection withHeldItems() {
        return new WorldProtection(active, world, buildMode, usable, rescue, true);
    }

    /** Doors, trapdoors and fence gates. */
    public static boolean passage(Block block) {
        var type = block.getType();
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        boolean opens = block != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && usable.test(block);
        if (opens || !restricts(event.getPlayer())) {
            return;
        }
        if (!heldItems) {
            event.setCancelled(true);
        } else if (block != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!byBuilder(event.getEntity())) {
            deny(event, event.getBlock().getWorld());
        }
    }

    @EventHandler
    public void onLostSupport(BlockDestroyEvent event) {
        deny(event, event.getBlock().getWorld());
    }

    @EventHandler
    public void onEntityTrigger(EntityInteractEvent event) {
        deny(event, event.getBlock().getWorld());
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (!byBuilder(event.getPlayer())) {
            deny(event, event.getBlock().getWorld());
        }
    }

    @EventHandler
    public void onFertilize(BlockFertilizeEvent event) {
        if (!byBuilder(event.getPlayer())) {
            deny(event, event.getBlock().getWorld());
        }
    }

    @EventHandler
    public void onPrime(TNTPrimeEvent event) {
        if (!byBuilder(event.getPrimingEntity())) {
            deny(event, event.getBlock().getWorld());
        }
    }

    @EventHandler
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!byBuilder(event.getPlayer())) {
            deny(event, event.getEntity().getWorld());
        }
    }

    @EventHandler
    public void onHatch(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG) {
            deny(event, event.getEntity().getWorld());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!byBuilder(event.getPlayer())) {
            deny(event, event.getEntity().getWorld());
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (!(event instanceof HangingBreakByEntityEvent byEntity && byBuilder(byEntity.getRemover()))) {
            deny(event, event.getEntity().getWorld());
        }
    }

    @EventHandler
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!byBuilder(event.getAttacker())) {
            deny(event, event.getVehicle().getWorld());
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!byBuilder(event.getAttacker())) {
            deny(event, event.getVehicle().getWorld());
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof Entity holder && !(holder instanceof Player)
                && event.getPlayer() instanceof Player player) {
            deny(event, player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        if (event.getWhoClicked() instanceof Player player
                && !(event.getClickedInventory() == top && InventoryInspection.permitted(player, top))) {
            deny(event, player);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            deny(event, player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        var victim = event.getEntity();
        boolean decoration = victim instanceof Hanging || victim instanceof ArmorStand;
        if (FATAL.contains(event.getCause()) || (decoration && byBuilder(event.getDamageSource().getCausingEntity()))
                || !protects(victim.getWorld())) {
            return;
        }
        event.setCancelled(true);
        victim.setFireTicks(0);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        deny(event, event.getEntity().getWorld());
    }

    @EventHandler
    public void onExperience(PlayerExpChangeEvent event) {
        if (protects(event.getPlayer().getWorld())) {
            event.setAmount(0);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        deny(event, event.getEntity().getWorld());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        deny(event, event.getBlock().getWorld());
    }

    @EventHandler
    public void onFall(PlayerMoveEvent event) {
        if (protects(event.getTo().getWorld()) && event.getTo().getY() < FALL_HEIGHT) {
            rescue.get().ifPresent(spawn -> {
                event.getPlayer().teleport(spawn);
                spawn.getWorld().playEffect(spawn, Effect.ENDER_SIGNAL, 0);
            });
        }
    }

    private void deny(Cancellable event, Player player) {
        if (restricts(player)) {
            event.setCancelled(true);
        }
    }

    private void deny(Cancellable event, World affected) {
        if (protects(affected)) {
            event.setCancelled(true);
        }
    }

    private boolean restricts(Player player) {
        return protects(player.getWorld()) && !buildMode.isEnabled(player);
    }

    private boolean byBuilder(Entity cause) {
        return cause instanceof Player player && buildMode.isEnabled(player);
    }

    private boolean protects(World affected) {
        return active.getAsBoolean() && world.map(affected::equals).orElse(true);
    }
}
