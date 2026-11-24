package me.blockcraft.lobby.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Figures;
import me.blockcraft.api.game.Leader;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.SpotEntities;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The leader of a board standing on its podium, at the map's {@code podium-<board>} spot, in their own skin and
 * named with their value; the figure changes when the board's first place does. A board nobody leads yet has an
 * empty podium.
 */
public final class Podiums implements AutoCloseable {

    private final Locations<String> spots;
    private final Leaderboards leaderboards;
    private final Messages messages;
    private final Map<String, Leader> standing = new HashMap<>();
    private final SpotEntities.Group<String> figures;

    public Podiums(SpotEntities entities, Locations<String> spots, Leaderboards leaderboards, Messages messages) {
        figures = entities.group();
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
            if (place.isPresent() && leader.isPresent() && leader.equals(Optional.ofNullable(standing.get(board)))
                    && figures.stands(board)) {
                continue;
            }
            figures.remove(board);
            standing.remove(board);
            if (place.isEmpty() || leader.isEmpty()) {
                continue;
            }
            var first = leader.get();
            figures.add(board, place.get(), Figures.spawn(place.get(), skin(first), messages.get("holo.leaderboard.podium",
                    Placeholder.unparsed("name", first.name()), Formatter.number("value", first.value()))));
            standing.put(board, first);
        }
    }

    /** Refreshes at once, for a podium just placed; the boards' own timer keeps them current afterwards. */
    public void placed() {
        close();
        refresh();
    }

    private static ResolvableProfile skin(Leader leader) {
        var profile = ResolvableProfile.resolvableProfile();
        leader.player().ifPresentOrElse(profile::uuid, () -> profile.name(leader.name()));
        return profile.build();
    }

    @Override
    public void close() {
        figures.close();
        standing.clear();
    }
}
