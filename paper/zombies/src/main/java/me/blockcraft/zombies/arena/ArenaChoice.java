package me.blockcraft.zombies.arena;

import me.blockcraft.api.game.MapVote;
import me.blockcraft.api.game.RoundScope;

import java.util.Optional;

/**
 * Which arena one round plays. The lobby's vote starts afresh with the round; the chosen map loads once the vote
 * closes and is prepared when the round starts.
 */
public final class ArenaChoice {

    private final ArenaMaps maps;
    private final RoundScope scope;
    private Optional<ArenaMap> chosen = Optional.empty();
    private boolean prepared;

    public ArenaChoice(ArenaMaps maps, RoundScope scope) {
        this.maps = maps;
        this.scope = scope;
        maps.vote().reopen();
    }

    /** The main world's arena, which /setup edits and the lobby without a shared waiting island stands in. */
    public ArenaMap main() {
        return maps.main();
    }

    public MapVote vote() {
        return maps.vote();
    }

    /** The name of the arena chosen so far: the vote's once it closed, the only map's without a vote. */
    public Optional<String> chosenName() {
        return chosen.map(ArenaMap::name).or(() -> vote().decided().map(MapVote.Choice::name))
                .or(() -> vote().offered() ? Optional.empty() : Optional.of(main().name()));
    }

    public boolean decided() {
        return vote().decided().isPresent();
    }

    /**
     * Closes the vote and loads its map's world, whose chunks then load in the background, unless that happened
     * already or the map's copy is still being made, which a later call loads once done.
     */
    public void decide() {
        var choice = vote().decide();
        if (chosen.isEmpty() && !maps.copying(choice)) {
            chosen = Optional.of(maps.load(choice, scope));
        }
    }

    /** Whether the chosen map's copy is still being made, which the start waits for rather than playing another map. */
    public boolean copying() {
        decide();
        return chosen.isEmpty();
    }

    /**
     * {@link #decide()}, with the arena prepared for its round: done at the start, once its chunks had time to load and
     * {@link #copying()} is false.
     */
    public ArenaMap prepared() {
        decide();
        var arena = chosen.orElseThrow();
        if (!prepared) {
            prepared = true;
            if (!arena.prepare() && !arena.world().equals(main().world())) {
                scope.getSLF4JLogger().error("The Zombies map {} is not ready; the round plays {} instead", arena.name(), main().name());
                var fallback = maps.mainFor(scope);
                fallback.prepare();
                chosen = Optional.of(fallback);
            }
        }
        return chosen.orElseThrow();
    }
}
