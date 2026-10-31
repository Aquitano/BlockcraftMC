package me.blockcraft.ttt.game;

/**
 * What a player's conduct costs or earns them, persisted between rounds: killing your own side costs
 * {@code teamKill}, an innocent or detective killing a traitor earns {@code enemyKill}, and a round without a team kill
 * earns {@code cleanRound}. Damage dealt scales with karma, never below {@code damageFloor}.
 */
public record Karma(int start, int max, int teamKill, int enemyKill, int cleanRound, double damageFloor) {

    public int afterKill(int karma, Role killer, Role victim) {
        if (killer.sameSide(victim)) {
            return clamp(karma - teamKill);
        }
        return killer == Role.TRAITOR ? karma : clamp(karma + enemyKill);
    }

    public int afterRound(int karma, boolean teamKilled) {
        return teamKilled ? karma : clamp(karma + cleanRound);
    }

    public double damageFactor(int karma) {
        return Math.clamp((double) karma / max, damageFloor, 1.0);
    }

    private int clamp(int karma) {
        return Math.clamp(karma, 0, max);
    }
}
