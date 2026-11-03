package me.blockcraft.sternenfront.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.sternenfront.combat.Combat;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.SternenfrontItem;
import me.blockcraft.sternenfront.hero.HeroAbility;
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
 * The round's controls: right click fires the blaster, guards with a blade or throws a streak's orbital strike.
 * F and Q use the first and second ability of the class or the hero. Only authored battlefield controls answer
 * block clicks during a round.
 */
public final class CombatListener implements Listener {

    private final SternenfrontGame game;
    private final BuildMode buildMode;

    public CombatListener(SternenfrontGame game, BuildMode buildMode) {
        this.game = game;
        this.buildMode = buildMode;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        if (!fighting(player)) {
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
        if (event.getClickedBlock() != null && game.battlefield().use(player, event.getClickedBlock())) {
            return;
        }
        if (Combat.holdsBlade(player)) {
            event.setUseItemInHand(Event.Result.ALLOW);
            game.combat().raise(player);
        } else {
            use(player, event.getItem());
        }
    }

    private void use(Player player, ItemStack item) {
        if (HotbarItem.of(item, Blaster.class).isPresent()) {
            game.pullTrigger(player);
            return;
        }
        HotbarItem.of(item, Ability.class).ifPresent(ability -> game.useAbility(player, ability));
        HotbarItem.of(item, HeroAbility.class).ifPresent(ability -> game.useHeroAbility(player, ability));
        HotbarItem.of(item, SternenfrontItem.class).ifPresent(roundItem -> {
            switch (roundItem) {
                case DEPLOY -> game.openDeploy(player);
                case BLADE, SABER -> game.combat().raise(player);
                case STRIKE -> game.throwStreakStrike(player);
            }
        });
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && fighting(event.getPlayer()) && !event.getPlayer().isHandRaised()) {
            game.swing(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        var player = event.getPlayer();
        if (!fighting(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
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
