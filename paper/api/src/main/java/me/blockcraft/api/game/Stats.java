package me.blockcraft.api.game;

public record Stats(int kills, int deaths, int wins, int losses, int points) {

    public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0);

    /** Kills per death rounded to two places, or "-" without a death. */
    public String kdr() {
        return deaths == 0 ? "-" : String.valueOf(Math.round((double) kills / deaths * 100.0) / 100.0);
    }
}
