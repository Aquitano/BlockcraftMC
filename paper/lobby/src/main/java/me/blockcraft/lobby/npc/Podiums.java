package me.blockcraft.lobby.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.Leader;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import me.blockcraft.lobby.activity.Figures;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The leader of a board standing on its podium, at the map's {@code podium-<board>} spot, in their own skin and
 * named with their value; the figure changes when the board's first place does. A board nobody leads yet has an
 * empty podium.
 */
public final class Podiums implements Listener, AutoCloseable {

    private record Standing(Leader leader, Mannequin figure) {
    }

    private final Locations<String> spots;
    private final Leaderboards leaderboards;
    private final Messages messages;
    private final Map<String, Standing> standing = new HashMap<>();
    private final ChunkTickets.Owner held;

    public Podiums(ChunkTickets chunks, Locations<String> spots, Leaderboards leaderboards, Messages messages) {
        held = chunks.owner();
        this.spots = spots;
        this.leaderboards = leaderboards;
        this.messages = messages;
    }

    public static String spot(String board) {
        return "podium-" + board;
    }

    /** Puts each board's leader as last read on its podium; a leader who did not change keeps standing. */
    public void refresh() {
        for (var board : leaderboards.names()) {
            var place = spots.get(spot(board));
            var leader = leaderboards.first(board);
            var current = Optional.ofNullable(standing.get(board));
            if (place.isEmpty() || leader.isEmpty()) {
                current.ifPresent(old -> old.figure().remove());
                standing.remove(board);
                continue;
            }
            if (current.filter(old -> old.leader().equals(leader.get()) && old.figure().isValid()).isPresent()) {
                continue;
            }
            current.ifPresent(old -> old.figure().remove());
            var chunk = place.get().getChunk();
            held.add(chunk);
            var first = leader.get();
            var figure = Figures.spawn(place.get(), skin(first), messages.get("holo.leaderboard.podium",
                    Placeholder.unparsed("name", first.name()), Formatter.number("value", first.value())));
            standing.put(board, new Standing(first, figure));
        }
    }

    /** Refreshes at once, for a podium just placed; the boards' own timer keeps them current afterwards. */
    public void placed() {
        standing.values().forEach(old -> old.figure().remove());
        standing.clear();
        refresh();
    }

    private static ResolvableProfile skin(Leader leader) {
        var profile = ResolvableProfile.resolvableProfile();
        leader.player().ifPresentOrElse(profile::uuid, () -> profile.name(leader.name()));
        return profile.build();
    }

    private boolean isFigure(Object entity) {
        return standing.values().stream().anyMatch(each -> each.figure().equals(entity));
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        if (isFigure(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (isFigure(event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void close() {
        standing.values().forEach(each -> each.figure().remove());
        standing.clear();
        held.clear();
    }
}
