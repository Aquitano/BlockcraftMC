package me.blockcraft.bedwars.lobby;

import me.blockcraft.api.config.Locations;
import me.blockcraft.bedwars.team.TeamColor;
import org.bukkit.Location;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The places of the waiting lobby, kept in the map's {@code lobby.yml}: the spawn, the CPS tester, the two ends of
 * the jump and run, and per team the stand that joins it and the spots where its members are shown.
 */
public final class LobbyLayout {

    public static final String SPAWN = "spawn";
    public static final String CPS_TESTER = "cps-tester";
    public static final String JUMP_START = "jump-and-run.start";
    public static final String JUMP_FINISH = "jump-and-run.finish";

    private final Locations<String> locations;

    private LobbyLayout(Locations<String> locations) {
        this.locations = locations;
    }

    public static LobbyLayout load(Path file) {
        return new LobbyLayout(Locations.load(file));
    }

    public Optional<Location> get(String key) {
        return locations.get(key);
    }

    public void set(String key, Location location) {
        locations.set(key, location);
    }

    public Optional<Location> joiner(TeamColor team) {
        return locations.get(joinerKey(team));
    }

    /** The spots in order, as many as were set without a gap. */
    public List<Location> displays(TeamColor team) {
        var spots = new ArrayList<Location>();
        for (int index = 0; ; index++) {
            var spot = locations.get(displayKey(team, index));
            if (spot.isEmpty()) {
                return spots;
            }
            spots.add(spot.get());
        }
    }

    public static String joinerKey(TeamColor team) {
        return "teams." + team.key() + ".joiner";
    }

    public static String displayKey(TeamColor team, int index) {
        return "teams." + team.key() + ".displays." + index;
    }
}
