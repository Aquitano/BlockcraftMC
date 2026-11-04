package me.blockcraft.common.elo;

public record EloRating(int rating, int wins, int losses) {

    public static EloRating fresh(EloFormula formula) {
        return new EloRating(formula.startRating(), 0, 0);
    }

    public EloRating won(int delta) {
        return new EloRating(rating + delta, wins + 1, losses);
    }

    public EloRating lost(int delta) {
        return new EloRating(rating - delta, wins, losses + 1);
    }
}
