package me.blockcraft.duell.game;

import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SuperJumpDuelGame extends MapDuelGame {
    private final Map<UUID, Integer> checkpoints = new HashMap<>();
    private final Set<UUID> fallen = new HashSet<>();

    SuperJumpDuelGame(DuelGames.Context context) {
        super(context);
    }

    @Override
    protected void equip(Player player) {
        PlayerReset.reset(player, GameMode.SURVIVAL);
    }

    @Override
    public int progress(UUID participant) {
        return checkpoints.getOrDefault(participant, 0);
    }

    @Override
    protected void died(PlayerDeathEvent event) {
        fallen.add(event.getPlayer().getUniqueId());
    }

    @Override
    public void respawn(PlayerRespawnEvent event) {
        var checkpoint = event.getPlayer().getRespawnLocation();
        super.respawn(event);
        if (checkpoint != null) {
            event.setRespawnLocation(checkpoint);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        var player = event.getPlayer();
        if (!active || !event.hasChangedBlock() || !participant(player)) {
            return;
        }
        var to = event.getTo();
        var block = to.getBlock();
        switch (block.getType()) {
            case HEAVY_WEIGHTED_PRESSURE_PLATE -> checkpoint(player, to, 1);
            case LIGHT_WEIGHTED_PRESSURE_PLATE -> checkpoint(player, to, 2);
            case WATER, LAVA -> {
                fallen.add(player.getUniqueId());
                var spawn = player.getRespawnLocation();
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
            default -> {
                if (block.getRelative(BlockFace.DOWN).getType() == Material.DIAMOND_BLOCK) {
                    if (!fallen.contains(player.getUniqueId())) {
                        context.summary().moment(player, "duell", "trittsicher");
                    }
                    Bukkit.broadcast(Component.empty());
                    Bukkit.broadcast(context.messages().get("superjump.goal", Placeholder.unparsed("player", player.getName())));
                    Bukkit.broadcast(Component.empty());
                    player.getWorld().spawnParticle(Particle.EXPLOSION, to, 5);
                    player.setRespawnLocation(to, true);
                    complete(java.util.Optional.of(player.getUniqueId()));
                }
            }
        }
    }

    private void checkpoint(Player player, Location plate, int checkpoint) {
        if (progress(player.getUniqueId()) >= checkpoint) {
            return;
        }
        checkpoints.put(player.getUniqueId(), checkpoint);
        player.setRespawnLocation(plate, true);
        Bukkit.broadcast(context.messages().get("superjump.checkpoint", Placeholder.unparsed("player", player.getName()),
                Formatter.number("checkpoint", checkpoint)));
        Sounds.NOTIFY.play(player);
        player.getWorld().spawnParticle(Particle.FLAME, plate, 5);
        context.events().changed();
    }
}
