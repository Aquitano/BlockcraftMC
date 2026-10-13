package me.blockcraft.duell.listener;

import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;

public final class SuperJumpListener implements Listener {

    private static final int CHECKPOINT_ONE = 1;
    private static final int CHECKPOINT_TWO = 2;

    private final Duel duel;

    public SuperJumpListener(Duel duel) {
        this.duel = duel;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        var player = event.getPlayer();
        if (!duel.isPlaying(DuelMode.SUPERJUMP) || !event.hasChangedBlock() || !duel.isParticipant(player)) {
            return;
        }
        var block = event.getTo().getBlock();
        switch (block.getType()) {
            case HEAVY_WEIGHTED_PRESSURE_PLATE -> duel.reachCheckpoint(player, CHECKPOINT_ONE);
            case LIGHT_WEIGHTED_PRESSURE_PLATE -> duel.reachCheckpoint(player, CHECKPOINT_TWO);
            case WATER, LAVA -> {
                duel.fell(player);
                Optional.ofNullable(player.getRespawnLocation()).ifPresent(player::teleport);
            }
            default -> {
                if (block.getRelative(BlockFace.DOWN).getType() == Material.DIAMOND_BLOCK) {
                    duel.finishSuperJump(player);
                }
            }
        }
    }
}
