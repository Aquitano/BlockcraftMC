package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Defeat;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class VsDuelGame extends MapDuelGame {
    private final Map<UUID, Integer> kills = new HashMap<>();

    VsDuelGame(DuelGames.Context context) {
        super(context);
    }

    @Override
    protected void equip(Player player) {
        super.equip(player);
        context.items().giveVsKit(player);
    }

    @Override
    public int progress(UUID participant) {
        return kills.getOrDefault(participant, 0);
    }

    @Override
    protected void died(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        var death = context.summary().resolveDeath(event);
        Bukkit.broadcast(death.line());
        context.summary().death(victim);
        var scorer = death.killer().filter(this::participant)
                .or(() -> players().stream().filter(other -> !other.equals(victim)).findFirst());
        scorer.ifPresent(killer -> {
            context.summary().awardKill(killer, victim, Defeat.POINT);
            int scored = kills.merge(killer.getUniqueId(), 1, Integer::sum);
            context.events().changed();
            if (scored > context.config().vsBestOf() / 2) {
                complete(Optional.of(killer.getUniqueId()));
            } else {
                int round = kills.values().stream().mapToInt(Integer::intValue).sum() + 1;
                BlockcraftApi.get().notifications().important(killer, Title.title(
                        context.messages().get("title.vs-round", Formatter.number("round", round)),
                        context.messages().get("title.start")));
            }
        });
    }

    @Override
    public void respawn(PlayerRespawnEvent event) {
        clearEntities();
        players().stream().filter(player -> !player.isDead() && !player.equals(event.getPlayer())).forEach(player -> {
            spawn(player).ifPresent(player::teleport);
            equip(player);
        });
        super.respawn(event);
    }
}
