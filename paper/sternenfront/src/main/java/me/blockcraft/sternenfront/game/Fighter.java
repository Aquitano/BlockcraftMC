package me.blockcraft.sternenfront.game;

import java.util.Optional;

/**
 * A player's state in a running round besides their faction: the class they deploy as next, whether they are on
 * the field or choosing a spawn, their blaster's heat, and the ticks until which they are protected, hurt last and
 * safe from their next fall.
 */
public final class Fighter {

    /** A dead player choosing: from {@code readyTick} on they may deploy, at {@code autoTick} they do. */
    public record Choosing(long readyTick, long autoTick, Optional<Spawn> chosen) {

        public Choosing choose(Spawn spawn) {
            return new Choosing(readyTick, autoTick, Optional.of(spawn));
        }
    }

    private CombatClass combatClass = CombatClass.SOLDIER;
    private Optional<Choosing> choosing = Optional.empty();
    private Heat heat;
    private long protectedUntil;
    private long lastHurt;
    private long fallSafeUntil;
    private boolean champion;

    public Fighter(Heat.Rules heatRules) {
        this.heat = new Heat(heatRules);
    }

    public CombatClass combatClass() {
        return combatClass;
    }

    public void combatClass(CombatClass combatClass) {
        this.combatClass = combatClass;
    }

    public Optional<Choosing> choosing() {
        return choosing;
    }

    public void choosing(Optional<Choosing> choosing) {
        this.choosing = choosing;
    }

    public boolean onField() {
        return choosing.isEmpty();
    }

    public Heat heat() {
        return heat;
    }

    /** A fresh deploy: a cold gun, protection until {@code protectedUntil}, no champion. */
    public void deployed(Heat.Rules heatRules, long protectedUntil) {
        this.choosing = Optional.empty();
        this.heat = new Heat(heatRules);
        this.protectedUntil = protectedUntil;
        this.champion = false;
    }

    public boolean isProtected(long tick) {
        return tick < protectedUntil;
    }

    public void endProtection() {
        protectedUntil = 0;
    }

    public long lastHurt() {
        return lastHurt;
    }

    public void hurt(long tick) {
        lastHurt = tick;
    }

    public boolean fallSafe(long tick) {
        return tick < fallSafeUntil;
    }

    public void fallSafeUntil(long tick) {
        fallSafeUntil = tick;
    }

    public boolean champion() {
        return champion;
    }

    public void champion(boolean champion) {
        this.champion = champion;
    }
}
