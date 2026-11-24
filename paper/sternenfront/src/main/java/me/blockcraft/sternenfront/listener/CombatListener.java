package me.blockcraft.sternenfront.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.sternenfront.combat.Combat;
import me.blockcraft.sternenfront.debug.BotDebug;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.SternenfrontItem;
import me.blockcraft.sternenfront.secret.Secrets;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The round's controls: left click fires the blaster or swings a blade, and right click aims the blaster or guards with
 * the blade; a long rifle looks through its scope while it aims. F and Q use the first and second ability of the class
 * or the hero. Only authored battlefield controls answer block clicks during a round. A click that the client spent on
 * one of the player's {@link me.blockcraft.sternenfront.game.TriggerCells} met no block of the world.
 */
public final class CombatListener implements Listener {

    private final SternenfrontGame game;
    private final BuildMode buildMode;
    private final BotDebug botDebug;
    private final Secrets secrets;

    public CombatListener(SternenfrontGame game, BuildMode buildMode, BotDebug botDebug, Secrets secrets) {
        this.game = game;
        this.buildMode = buildMode;
        this.botDebug = botDebug;
        this.secrets = secrets;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        if (!fighting(player) || botDebug.enabled(player)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        var clicked = event.getClickedBlock();
        boolean cell = clicked != null && game.triggerCells().covers(player, clicked);
        if (cell && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            game.triggerStarted(player);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        if (cell) {
            // The server answers this use with the cell's real block. Hidden first, no neighbouring cell is left for the look to meet.
            game.triggerCells().hide(player);
            clicked = lookedAt(player);
        }
        if (clicked != null && secrets.use(player, clicked)) {
            return;
        }
        if (game.speeders().fire(player)) {
            return;
        }
        if (clicked != null && game.battlefield().use(player, clicked)) {
            return;
        }
        if (Combat.holdsBlade(player)) {
            event.setUseItemInHand(Event.Result.ALLOW);
            game.combat().raise(player);
        } else {
            use(player, event.getItem());
        }
    }

    private static Block lookedAt(Player player) {
        var reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        var hit = reach == null ? null : player.rayTraceBlocks(reach.getValue());
        return hit == null ? null : hit.getHitBlock();
    }

    private void use(Player player, ItemStack item) {
        if (HotbarItem.of(item, Blaster.class).isPresent()) {
            game.raiseSights(player);
            return;
        }
        HotbarItem.of(item, SternenfrontItem.class).ifPresent(roundItem -> {
            switch (roundItem) {
                case DEPLOY -> game.deployment().openDeploy(player);
                case BLADE, SABER -> game.combat().raise(player);
            }
        });
    }

    /** Paper fires this once for each left click, whether it met air, a block or an entity. */
    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        var player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !fighting(player) || game.speeders().fire(player)) {
            return;
        }
        if (holdsBlaster(player)) {
            // A held trigger swings every tick, and the gun shows its own kick.
            event.setCancelled(true);
            game.triggerSwung(player);
        } else if (!player.isHandRaised()) {
            game.swing(player);
        }
    }

    @EventHandler
    public void onStopBreaking(BlockDamageAbortEvent event) {
        if (fighting(event.getPlayer())) {
            game.triggerStopped(event.getPlayer());
        }
    }

    /** A left click on someone close with a blaster in hand is a shot, which {@link #onSwing} fires, and no punch. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (fighting(event.getPlayer()) && holdsBlaster(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private static boolean holdsBlaster(Player player) {
        return HotbarItem.of(player.getInventory().getItemInMainHand(), Blaster.class).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        var player = event.getPlayer();
        if (!fighting(player) || botDebug.enabled(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND && !game.speeders().fire(player)) {
            use(player, player.getInventory().getItemInMainHand());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (fighting(event.getPlayer())) {
            event.setCancelled(true);
            if (event.getPlayer().isSneaking() && game.strikeReady(event.getPlayer())) {
                game.throwStreakStrike(event.getPlayer());
            } else {
                game.useAbility(event.getPlayer(), 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (fighting(event.getPlayer())) {
            event.setCancelled(true);
            // A cancelled drop restores the weapon after this event, before the ability reads its slot.
            Tasks.sync(game.plugin(), () -> {
                if (event.getPlayer().isOnline() && fighting(event.getPlayer())) {
                    game.useAbility(event.getPlayer(), 1);
                }
            });
        }
    }

    private boolean fighting(Player player) {
        return game.phase() == GamePhase.INGAME && game.isPlaying(player) && !buildMode.isEnabled(player);
    }
}
