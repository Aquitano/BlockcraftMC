package me.blockcraft.ttt.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** How many traitors and detectives a round of {@code players} gets; everyone else is innocent. */
public record Roles(double traitorRatio, double detectiveRatio, int detectiveMinPlayers) {

    public int traitors(int players) {
        return Math.min(players, Math.max(1, (int) Math.floor(players * traitorRatio)));
    }

    public int detectives(int players) {
        if (players < detectiveMinPlayers) {
            return 0;
        }
        return Math.min(players - traitors(players), Math.max(1, (int) Math.floor(players * detectiveRatio)));
    }

    /** Hands out the roles in the order of {@code shuffled}: traitors first, then detectives, then the innocents. */
    public Map<UUID, Role> assign(List<UUID> shuffled) {
        int traitors = traitors(shuffled.size());
        int detectives = detectives(shuffled.size());
        var roles = new LinkedHashMap<UUID, Role>();
        for (int index = 0; index < shuffled.size(); index++) {
            roles.put(shuffled.get(index), index < traitors ? Role.TRAITOR
                    : index < traitors + detectives ? Role.DETECTIVE : Role.INNOCENT);
        }
        return roles;
    }
}
