package me.blockcraft.sternenfront.arena;

import me.blockcraft.api.game.MapVote;
import me.blockcraft.api.game.RoundScope;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Which front one round is fought on. The lobby's vote starts afresh with the round; the chosen map's copy loads once
 * the vote closes, before the round starts.
 */
public final class FrontChoice {

    private final FrontMaps maps;
    private final RoundScope scope;
    private Optional<FrontMaps.Front> chosen = Optional.empty();

    public FrontChoice(FrontMaps maps, RoundScope scope) {
        this.maps = maps;
        this.scope = scope;
        maps.vote().reopen();
    }

    /** The main world's map, where the lobby stands without a shared waiting island. */
    public FrontMap main() {
        return maps.main();
    }

    public MapVote vote() {
        return maps.vote();
    }

    /** The name of the map chosen so far: the vote's once it closed, the only map's without a vote. */
    public Optional<String> chosenName() {
        return chosen.map(front -> front.map().name()).or(() -> vote().offered() ? Optional.empty() : Optional.of(main().name()));
    }

    public boolean decided() {
        return chosen.isPresent();
    }

    /** A map-owned file of the chosen map, {@code blockcraft/Sternenfront/<name>}; the main world's until one is chosen. */
    public Path file(String name) {
        return chosen.map(front -> front.files().apply(name)).orElseGet(() -> maps.mainFile(name));
    }

    /** Closes the vote and loads its map's copy, unless that happened already. */
    public FrontMap decide() {
        if (chosen.isEmpty()) {
            chosen = Optional.of(maps.load(vote().decide(), scope));
        }
        return chosen.orElseThrow().map();
    }
}
