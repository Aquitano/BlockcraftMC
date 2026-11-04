package me.blockcraft.common.elo;

/** One settled match: the points that moved and where both players stand afterwards. */
public record EloMatch(int delta, EloRating winner, EloRating loser) {

    public int winnerBefore() {
        return winner.rating() - delta;
    }

    public int loserBefore() {
        return loser.rating() + delta;
    }
}
