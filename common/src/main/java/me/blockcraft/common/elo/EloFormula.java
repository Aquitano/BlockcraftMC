package me.blockcraft.common.elo;

/** Standard Elo: the winner takes {@code kFactor} times how unexpected the win was, the loser gives the same. */
public record EloFormula(int startRating, int kFactor) {

    private static final double SPREAD = 400.0;

    public double expectedWin(int rating, int opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponent - rating) / SPREAD));
    }

    public int delta(int winner, int loser) {
        return (int) Math.round(kFactor * (1.0 - expectedWin(winner, loser)));
    }
}
