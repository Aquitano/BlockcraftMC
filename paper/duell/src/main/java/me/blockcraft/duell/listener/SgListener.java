package me.blockcraft.duell.listener;

import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SgListener implements Listener {

    private final Duel duel;
    private final LootChests lootChests;

    public SgListener(Duel duel, LootChests lootChests) {
        this.duel = duel;
        this.lootChests = lootChests;
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (!duel.isPlaying(DuelMode.SG) || !duel.isParticipant(event.getPlayer()) || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        if (lootChests.isFresh(block.getLocation())) {
            Sounds.MENU_OPEN.play(player);
        }
        player.openInventory(lootChests.open(block.getLocation()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!duel.isPlaying(DuelMode.SG)) {
            return;
        }
        event.deathMessage(null);
        var victim = event.getPlayer();
        duel.handleSgDeath(victim, victim.getKiller());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (duel.isLobby() || !duel.isMode(DuelMode.SG)) {
            return;
        }
        duel.lobbySpawn().ifPresent(spawn -> {
            event.setRespawnLocation(spawn);
            duel.opponentsOf(event.getPlayer()).forEach(other -> {
                other.teleport(spawn);
                other.setFireTicks(0);
            });
        });
        event.getPlayer().setFireTicks(0);
    }
}
