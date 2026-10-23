package me.blockcraft.duell.game;

import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.sound.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class SgDuelGame extends MapDuelGame {
    private final LootChests chests;

    SgDuelGame(DuelGames.Context context, LootChests chests) {
        super(context);
        this.chests = chests;
        chests.clear();
    }

    @Override
    protected void died(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        var death = context.summary().resolveDeath(event);
        Bukkit.broadcast(death.line());
        context.summary().death(victim);
        death.killer().filter(this::participant).ifPresent(killer -> context.summary().awardKill(killer, victim, Defeat.OUT));
        complete(context.participants().stream().filter(id -> !id.equals(victim.getUniqueId())).findFirst());
    }

    @EventHandler
    public void onChest(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (!active || !participant(event.getPlayer()) || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) {
            return;
        }
        event.setCancelled(true);
        if (chests.isFresh(block.getLocation())) {
            Sounds.MENU_OPEN.play(event.getPlayer());
        }
        event.getPlayer().openInventory(chests.open(block.getLocation()));
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (participant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (participant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void close() {
        super.close();
        chests.clear();
    }
}
