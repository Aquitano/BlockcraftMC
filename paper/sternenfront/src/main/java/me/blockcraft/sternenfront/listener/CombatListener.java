package me.blockcraft.sternenfront.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The round's controls: left click fires the blaster or swings a blade, and right click aims the blaster or guards with
 * the blade. A long rifle looks through its scope while right click is held and fires when it is let go, since the
 * client sends no clicks while it scopes. F and Q use the first and second ability of the class or the hero. Only
 * authored battlefield controls answer block clicks during a round.
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
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        if (event.getClickedBlock() != null && secrets.use(player, event.getClickedBlock())) {
            return;
        }
        if (game.speeders().fire(player)) {
            return;
        }
        if (event.getClickedBlock() != null && game.battlefield().use(player, event.getClickedBlock())) {
            return;
        }
        if (Combat.holdsBlade(player)) {
            event.setUseItemInHand(Event.Result.ALLOW);
            game.combat().raise(player);
        } else if (longRifle(event.getItem())) {
            event.setUseItemInHand(Event.Result.ALLOW);
        } else {
            use(player, event.getItem());
        }
    }

    private void use(Player player, ItemStack item) {
        if (longRifle(item)) {
            return;
        }
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

    /** A tap fires from the hip; a longer hold has looked through the scope, and the shot settles as it does. */
    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        if (fighting(event.getPlayer()) && longRifle(event.getItem())) {
            game.pullTrigger(event.getPlayer());
        }
    }

    private static boolean longRifle(ItemStack item) {
        return HotbarItem.of(item, Blaster.class).filter(Blaster::isLongRifle).isPresent();
    }

    /** Paper fires this once for each left click, whether it met air, a block or an entity. */
    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        var player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !fighting(player) || game.speeders().fire(player)) {
            return;
        }
        if (holdsBlaster(player)) {
            game.pullTrigger(player);
        } else if (!player.isHandRaised()) {
            game.swing(player);
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
