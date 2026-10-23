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
import java.util.UUID;

final class VsDuelGame extends MapDuelGame {
    private final Map<UUID, Integer> kills = new HashMap<>();
    private int round = 1;

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
        death.killer().filter(this::participant).ifPresent(killer -> {
            context.summary().awardKill(killer, victim, Defeat.POINT);
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            round++;
            context.events().changed();
            if (round > context.config().vsKillsToEnd()) {
                complete(java.util.Optional.of(progress(victim.getUniqueId()) > progress(killer.getUniqueId())
                        ? victim.getUniqueId() : killer.getUniqueId()));
            } else {
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
