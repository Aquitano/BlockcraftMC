package me.blockcraft.ttt.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.game.Role;
import me.blockcraft.ttt.game.RoundItem;
import me.blockcraft.ttt.game.TttGame;
import me.blockcraft.ttt.shop.ShopItem;
import me.blockcraft.ttt.task.TaskItem;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;
import java.util.Set;

/**
 * The round's rules for the town: nothing is built or broken but what the shop sells, explosions leave the houses
 * standing, nobody is hurt or hungry before the roles are drawn, and blows land as hard as the attacker's karma
 * allows. The town's barrels, workbenches and flower pots answer nothing; its chests and ender chests, the tester's
 * and the traitor trap's buttons, the traitor doors, TTT 2's chores, the corpses and whatever the shop's goods left in
 * the world answer a click. A good in hand never keeps a door from opening.
 */
public final class ArenaListener implements Listener {

    private static final double LETHAL = 1000.0;
    private static final Set<Material> WORKSTATIONS = Set.of(Material.CRAFTING_TABLE, Material.LOOM,
            Material.CARTOGRAPHY_TABLE, Material.SMITHING_TABLE, Material.STONECUTTER, Material.GRINDSTONE,
            Material.ENCHANTING_TABLE);

    private final TttGame game;
    private final Messages messages;

    public ArenaListener(TttGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inRound()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (inRound()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        var player = event.getPlayer();
        if (inRound() && !(game.rolesDrawn() && game.isPlaying(player)
                && game.equipment().place(player, event.getItemInHand(), event.getBlockPlaced()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        if (!inRound() || !game.isPlaying(player)) {
            return;
        }
        var block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL) {
            if (block != null && game.rolesDrawn()) {
                game.equipment().step(player, block);
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        if (block != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (clickBlock(player, block)) {
                event.setCancelled(true);
                return;
            }
            if (scenery(block)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
        var hand = event.getItem();
        if (HotbarItem.of(hand, RoundItem.class).isPresent()) {
            event.setCancelled(true);
            game.openShop(player);
        } else if (hand != null && game.rolesDrawn()
                && game.equipment().use(player, hand, Optional.ofNullable(block), event.getBlockFace())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** True if the click was the town's to answer. */
    private boolean clickBlock(Player player, Block block) {
        if (game.equipment().openFake(player, block) || game.openChest(player, block)
                || game.openTraitorDoor(player, block) || game.clickChore(player, block)) {
            return true;
        }
        if (block.getType() == Material.ENDER_CHEST) {
            game.openEnderChest(player);
            return true;
        }
        if (game.isTesterButton(block)) {
            game.pressTester(player);
        } else if (game.isTrapButton(block)) {
            game.pressTrap(player);
        }
        return false;
    }

    /** The town's barrels, workbenches and flower pots are scenery: nothing is stored, made or taken there. */
    private static boolean scenery(Block block) {
        var type = block.getType();
        return WORKSTATIONS.contains(type) || Tag.ANVIL.isTagged(type) || Tag.FLOWER_POTS.isTagged(type)
                || block.getState(false) instanceof InventoryHolder;
    }

    /** A corpse, or something the shop's goods left in the world: a time bomb, a motion sensor. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {
        var player = event.getPlayer();
        var clicked = event.getRightClicked();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (game.corpses().isCorpse(clicked)) {
            event.setCancelled(true);
            if (inRound() && game.isPlaying(player)) {
                game.inspect(player, clicked, player.getInventory().getItemInMainHand());
            }
        } else if (inRound() && game.isPlaying(player) && game.equipment().clickGadget(player, clicked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock() && inRound() && game.rolesDrawn() && game.isPlaying(event.getPlayer())) {
            game.equipment().moved(event.getPlayer(), event.getTo().getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !inRound()) {
            return;
        }
        if (!game.rolesDrawn()) {
            event.setCancelled(true);
            return;
        }
        game.channels().interrupt(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !inRound()) {
            return;
        }
        var damager = event.getDamager();
        var attacker = switch (damager) {
            case Player player -> player;
            case Projectile projectile when projectile.getShooter() instanceof Player shooter -> shooter;
            default -> null;
        };
        event.setDamage(event.getDamage() * game.equipment().damageTaken(victim));
        if (attacker == null || attacker.equals(victim) || !game.isPlaying(attacker)) {
            return;
        }
        if (game.equipment().stunned(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (damager == attacker && (goldenSword(event, attacker, victim) || backstab(event, attacker, victim))) {
            return;
        }
        event.setDamage(event.getDamage() * game.damageFactor(attacker));
    }

    /** The detective's golden sword kills a traitor with one blow, and its wielder for striking anyone else. */
    private boolean goldenSword(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        var hand = attacker.getInventory().getItemInMainHand();
        if (HotbarItem.of(hand, ShopItem.class).filter(item -> item == ShopItem.GOLDEN_SWORD).isEmpty()) {
            return false;
        }
        attacker.getInventory().setItemInMainHand(null);
        if (game.roleOf(victim).filter(role -> role == Role.TRAITOR).isPresent()) {
            event.setDamage(LETHAL);
            return true;
        }
        event.setCancelled(true);
        attacker.sendMessage(messages.get("shop.golden-sword-wrong"));
        game.blameSelf(attacker);
        attacker.damage(LETHAL, DamageSource.builder(DamageType.MAGIC).build());
        return true;
    }

    private boolean backstab(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (!game.equipment().backstab(attacker, victim)) {
            return false;
        }
        event.setDamage(LETHAL);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (game.equipment().stunned(shooter)) {
            event.setCancelled(true);
            return;
        }
        if (event.getConsumable() != null) {
            game.equipment().shoot(shooter, event.getConsumable(), event.getProjectile());
        }
    }

    /** A hit SpectatorGuard cancelled passed through a spectator; the arrow flies on and must not hatch there. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        game.equipment().land(event.getEntity());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (inRound()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        var stack = event.getItemDrop().getItemStack();
        if (HotbarItem.of(stack, RoundItem.class).isPresent() || HotbarItem.of(stack, TaskItem.class).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        game.death(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        game.respawn(event);
    }

    private boolean inRound() {
        return game.phase() == GamePhase.INGAME;
    }
}
