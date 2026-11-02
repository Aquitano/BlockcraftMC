package me.blockcraft.common.player;

import java.util.UUID;

/** Two players as a table keyed by both stores them: ordered, so a pair has one row whoever is named first. */
public record PlayerPair(String first, String second) {

    public static PlayerPair of(UUID one, UUID two) {
        String a = one.toString();
        String b = two.toString();
        return a.compareTo(b) <= 0 ? new PlayerPair(a, b) : new PlayerPair(b, a);
    }
}
