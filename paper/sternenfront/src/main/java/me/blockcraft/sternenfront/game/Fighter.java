package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.hero.Hero;

import java.util.Optional;

/**
 * A player's state in a running round besides their faction: the class they deploy as next, the hero they fight
 * as, whether they are on the field or choosing a spawn, their blaster's heat, and the ticks until which they are
 * protected, hurt last and safe from their next fall.
 */
public final class Fighter {

    /**
     * A dead player choosing: from {@code readyTick} on they may deploy, at {@code autoTick} they do, as {@code hero}
     * if they picked one and can still pay for it.
     */
    public record Choosing(long readyTick, long autoTick, Optional<Spawn> chosen, Optional<Hero> hero) {

        public Choosing choose(Spawn spawn) {
            return new Choosing(readyTick, autoTick, Optional.of(spawn), hero);
        }

        public Choosing pick(Optional<Hero> hero) {
            return new Choosing(readyTick, autoTick, chosen, hero);
        }
    }

    private CombatClass combatClass = CombatClass.SOLDIER;
    private Optional<Choosing> choosing = Optional.empty();
    private Heat heat;
    private long protectedUntil;
    private long lastHurt;
    private long fallSafeUntil;
    private Optional<Hero> hero = Optional.empty();

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

    /** A fresh deploy: a cold gun, protection until {@code protectedUntil}, as {@code hero} or as the class. */
    public void deployed(Heat.Rules heatRules, long protectedUntil, Optional<Hero> hero) {
        this.choosing = Optional.empty();
        this.heat = new Heat(heatRules);
        this.protectedUntil = protectedUntil;
        this.hero = hero;
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

    public Optional<Hero> hero() {
        return hero;
    }

    public void hero(Optional<Hero> hero) {
        this.hero = hero;
    }

    /** The blaster in this fighter's hands: the class's, a gun hero's own, or none for a blade. */
    public Optional<Blaster> blaster(Faction faction) {
        return hero.map(Hero::weapon).map(weapon -> weapon instanceof Hero.Weapon.Gun gun ? gun.blaster() : null)
                .or(() -> hero.isPresent() ? Optional.empty() : Optional.of(combatClass.blaster(faction)));
    }
}
