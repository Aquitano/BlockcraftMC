package me.blockcraft.duell.game;

import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.rush.RushArena;
import me.blockcraft.api.game.rush.RushConfig;
import me.blockcraft.api.game.rush.RushKit;
import me.blockcraft.api.game.rush.RushSession;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class MlgRushDuelGame implements DuelGame {
    private final RushSession session;
    private final RushArena arena;
    private final List<UUID> participants;
    private final RoundSummary summary;
    private final RushConfig config;
    private final Messages messages;
    private final int[] scores = new int[2];
    private boolean closed;

    MlgRushDuelGame(Plugin plugin, RushConfig config, Messages messages, RushArena arena,
                    List<UUID> participants, RoundSummary summary, DuelGames.Events events) {
        this.arena = arena;
        this.summary = summary;
        this.config = config;
        this.messages = messages;
        this.participants = List.copyOf(participants);
        session = new RushSession(plugin, config, arena, new RushKit(config.kit(), messages, "mlgrush.item-"), participants,
                new RushSession.Events() {
                    @Override
                    public void fell(Player player) {
                        summary.death(player);
                    }

                    @Override
                    public void scoreChanged() {
                        for (int slot = 0; slot < participants.size(); slot++) {
                            var id = participants.get(slot);
                            int score = progress(id);
                            if (score > scores[slot]) {
                                var scorer = Bukkit.getPlayer(id);
                                if (scorer != null) {
                                    Bukkit.broadcast(messages.get("mlgrush.point", Placeholder.unparsed("player", scorer.getName()),
                                            Formatter.number("score", score), Formatter.number("target", config.scoreTarget())));
                                }
                            }
                            scores[slot] = score;
                        }
                        events.changed();
                    }

                    @Override
                    public void won(UUID player) {
                        events.completed(Optional.of(player));
                    }
                });
        Bukkit.broadcast(messages.get("mlgrush.started", Formatter.number("target", config.scoreTarget())));
    }

    @Override
    public void activate() {
        session.activate();
    }

    @Override
    public void respawn(PlayerRespawnEvent event) {
        int slot = participants.indexOf(event.getPlayer().getUniqueId());
        if (slot >= 0) {
            event.setRespawnLocation(arena.spawn(slot));
        }
    }

    @Override
    public void spectate(Player player) {
        player.teleport(arena.spectatorSpawn());
    }

    @Override
    public int progress(UUID participant) {
        return session.score(participant);
    }

    @Override
    public Optional<UUID> timeoutWinner() {
        var first = participants.getFirst();
        var second = participants.getLast();
        return progress(first) == progress(second) ? Optional.empty()
                : Optional.of(progress(first) > progress(second) ? first : second);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var id : participants) {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                summary.note(player, messages.get("mlgrush.result", Formatter.number("score", progress(id)),
                        Formatter.number("target", config.scoreTarget())));
            }
        }
        session.close();
    }
}
