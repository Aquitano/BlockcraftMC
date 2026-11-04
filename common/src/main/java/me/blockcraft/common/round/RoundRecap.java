package me.blockcraft.common.round;

/**
 * What a round paid one player and how it ended, waiting for them in the lobby. {@code game} is the server group
 * the round ran in; {@code winners} are the names as the round showed them, empty for a draw.
 */
public record RoundRecap(String game, boolean won, String winners, int kills, int deaths, int bestStreak, int coins, int xp) {
}
