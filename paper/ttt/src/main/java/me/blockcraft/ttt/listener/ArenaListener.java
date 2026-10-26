package me.blockcraft.ttt.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.game.Role;
import me.blockcraft.ttt.game.RoundItem;
import me.blockcraft.ttt.game.TttGame;
import me.blockcraft.ttt.shop.ShopItem;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The round's rules for the town: nothing is built or broken but what the shop sells, explosions leave the houses
 * standing, nobody is hurt or hungry before the roles are drawn, and blows land as hard as the attacker's karma
 * allows. Town chests, the ender chest, the tester's button and the corpses answer a click.
 */
public final class ArenaListener implements Listener {

    private static final double LETHAL = 1000.0;

    private final TttGame game;
    private final Messages messages;

    public ArenaListener(TttGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
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
        if (block != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && clickBlock(player, block)) {
            event.setCancelled(true);
            return;
        }
        var hand = event.getItem();
        if (HotbarItem.of(hand, RoundItem.class).isPresent()) {
            event.setCancelled(true);
            game.openShop(player);
        } else if (hand != null && game.rolesDrawn() && game.equipment().heal(player, hand)) {
            event.setCancelled(true);
        }
    }

    /** True if the click was the town's to answer. */
    private boolean clickBlock(Player player, Block block) {
        if (game.equipment().openFake(player, block) || game.openChest(player, block)) {
            return true;
        }
        if (block.equals(game.town().enderChest())) {
            game.openEnderChest(player);
            return true;
        }
        if (game.tester().isButton(block)) {
            game.pressTester(player);
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCorpse(PlayerInteractEntityEvent event) {
        var player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !game.corpses().isCorpse(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (inRound() && game.isPlaying(player)) {
            game.inspect(player, event.getRightClicked(), player.getInventory().getItemInMainHand());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && inRound() && !game.rolesDrawn()) {
            event.setCancelled(true);
        }
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
        if (attacker == null || attacker.equals(victim) || !game.isPlaying(attacker)) {
            return;
        }
        if (damager == attacker && goldenSword(event, attacker, victim)) {
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
        attacker.damage(LETHAL, DamageSource.builder(DamageType.MAGIC).build());
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player shooter && event.getConsumable() != null) {
            game.equipment().shoot(shooter, event.getConsumable(), event.getProjectile());
        }
    }

    @EventHandler
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
        if (HotbarItem.of(event.getItemDrop().getItemStack(), RoundItem.class).isPresent()) {
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
