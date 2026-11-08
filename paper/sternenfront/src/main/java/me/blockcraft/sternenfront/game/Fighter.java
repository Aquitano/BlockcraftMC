package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A player's state in a running round besides their faction: the class they deploy as next, the hero or reinforcement they fight
 * as, whether they are on the field or choosing a spawn, their blaster's heat, and the ticks until which they are
 * protected, hurt last and safe from their next fall.
 */
public final class Fighter {

    private static final long LONG_AGO = Long.MIN_VALUE / 2;

    /**
     * A dead player choosing: from {@code readyTick} on they may deploy, at {@code autoTick} they do. A hero or
     * reinforcement is bought only after explicit confirmation and revalidation at deployment.
     */
    public record Choosing(long readyTick, long autoTick, Optional<Spawn> chosen, Optional<Hero> hero,
                           Optional<Reinforcement> reinforcement, boolean confirmed) {

        public Choosing {
            autoTick = Math.max(readyTick, autoTick);
        }

        public static Choosing begin(long readyTick, long autoTick) {
            return new Choosing(readyTick, autoTick, Optional.empty(), Optional.empty(), Optional.empty(), false);
        }

        public boolean manualReady(long tick) {
            return tick >= readyTick && chosen.isPresent() && confirmed;
        }

        public Optional<Hero> paidHero() {
            return hero.filter(ignored -> confirmed);
        }

        public Optional<Reinforcement> paidReinforcement() {
            return reinforcement.filter(ignored -> confirmed);
        }

        public Choosing choose(Spawn spawn) {
            return new Choosing(readyTick, autoTick, Optional.of(spawn), hero, reinforcement, true);
        }

        public Choosing retry(long autoTick) {
            return new Choosing(readyTick, autoTick, chosen, hero, reinforcement, false);
        }

        public Choosing pick(Optional<Hero> hero) {
            return new Choosing(readyTick, autoTick, chosen, hero, Optional.empty(),
                    confirmed && reinforcement.isEmpty() && this.hero.equals(hero));
        }

        public Choosing pick(Reinforcement unit) {
            return new Choosing(readyTick, autoTick, chosen, Optional.empty(), Optional.of(unit),
                    confirmed && hero.isEmpty() && reinforcement.filter(unit::equals).isPresent());
        }
    }

    private CombatClass combatClass = CombatClass.SOLDIER;
    private Optional<Choosing> choosing = Optional.empty();
    private Heat heat;
    private long protectedUntil;
    private long lastHurt;
    private long fallSafeUntil;
    private long sprintedAt = LONG_AGO;
    private final Map<Ability, Charges> charges = new EnumMap<>(Ability.class);
    private final Function<Ability, Charges> freshCharges;
    private Optional<Hero> hero = Optional.empty();
    private Optional<Reinforcement> reinforcement = Optional.empty();

    public Fighter(Heat.Rules heatRules, Function<Ability, Charges> freshCharges) {
        this.heat = new Heat(heatRules);
        this.freshCharges = freshCharges;
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
        this.sprintedAt = LONG_AGO;
        this.charges.clear();
        this.hero = hero;
        this.reinforcement = Optional.empty();
    }

    /** The uses of a class ability left in this life, full on each deployment. */
    public Charges charges(Ability ability) {
        return charges.computeIfAbsent(ability, freshCharges);
    }

    public void sprinted(long tick) {
        sprintedAt = tick;
    }

    public long sprintedAt() {
        return sprintedAt;
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

    public void makeFallSafeUntil(long tick) {
        fallSafeUntil = tick;
    }

    public Optional<Hero> hero() {
        return hero;
    }

    public void hero(Optional<Hero> hero) {
        this.hero = hero;
        this.reinforcement = Optional.empty();
    }

    public Optional<Reinforcement> reinforcement() {
        return reinforcement;
    }

    public void reinforcement(Optional<Reinforcement> reinforcement) {
        this.reinforcement = reinforcement;
        if (reinforcement.isPresent()) {
            hero = Optional.empty();
        }
    }

    public List<Ability> abilities(Faction faction) {
        return hero.isPresent() ? List.of() : reinforcement.map(Reinforcement::abilities).orElseGet(() -> combatClass.abilities(faction));
    }

    /** The current unit's blaster, or none for a blade. */
    public Optional<Blaster> blaster(Faction faction) {
        if (hero.isPresent()) {
            return hero.map(Hero::weapon).filter(Hero.Weapon.Gun.class::isInstance).map(Hero.Weapon.Gun.class::cast).map(Hero.Weapon.Gun::blaster);
        }
        return reinforcement.map(Reinforcement::blaster).or(() -> Optional.of(combatClass.blaster(faction)));
    }
}
