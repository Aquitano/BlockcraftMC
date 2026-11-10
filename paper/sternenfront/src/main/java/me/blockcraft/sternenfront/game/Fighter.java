package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * A player's state in a running round besides their faction: the class they deploy as next, the hero or reinforcement they fight
 * as, whether they are on the field or choosing a spawn, their blaster's heat, and the ticks until which they are
 * protected, hurt last and safe from their next fall.
 */
public final class Fighter {

    private static final long LONG_AGO = Long.MIN_VALUE / 2;
    /** A late packet may stretch one gap between a held use's repeats from 4 ticks to 6. */
    private static final int AIM_HELD_TICKS = 6;
    private static final int AIM_ASK_TICKS = 10;
    /** A gun brought up out of a sprint stays up this long after its last shot or aim, so a pause between shots does not lower it. */
    private static final int BRACED_TICKS = 12;

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

        public Optional<Hero> confirmedHero() {
            return hero.filter(ignored -> confirmed);
        }

        public Optional<Reinforcement> confirmedReinforcement() {
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
    private long lastHurt = LONG_AGO;
    private long fallSafeUntil;
    private long sprintedAt = LONG_AGO;
    private long aimedAt = LONG_AGO;
    private long scopedSince = LONG_AGO;
    private long queuedPull = Long.MAX_VALUE;
    private long bracedUntil = LONG_AGO;
    private long usedAt = LONG_AGO;
    private long aimAskAt = LONG_AGO;
    private long aimAskedAt = LONG_AGO;
    private boolean aimAskPending;
    private Trigger trigger = new Trigger();
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

    /** A fresh deploy: a cold gun, unhurt, protection until {@code protectedUntil}, as {@code hero} or as the class. */
    public void deployed(Heat.Rules heatRules, long protectedUntil, Optional<Hero> hero) {
        this.choosing = Optional.empty();
        this.heat = new Heat(heatRules);
        this.protectedUntil = protectedUntil;
        this.lastHurt = LONG_AGO;
        this.fallSafeUntil = 0;
        this.sprintedAt = LONG_AGO;
        this.aimedAt = LONG_AGO;
        this.scopedSince = LONG_AGO;
        this.queuedPull = Long.MAX_VALUE;
        this.bracedUntil = LONG_AGO;
        this.usedAt = LONG_AGO;
        this.aimAskAt = LONG_AGO;
        this.aimAskedAt = LONG_AGO;
        this.aimAskPending = false;
        this.trigger = new Trigger();
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

    /** A held right click reaches the server as a use every 4 ticks, so the last one tells whether it is still held. */
    public void aimed(long tick) {
        aimedAt = tick;
        usedAt = tick;
    }

    public boolean aiming(long tick) {
        return tick - aimedAt <= AIM_HELD_TICKS;
    }

    /**
     * A client that holds its trigger sends no uses, so the aim it began the hold with carries on. It sends one again
     * when its trigger cells are taken away for a moment, and once that has made it stop, only such a use keeps the aim.
     */
    public void holdAim(long tick) {
        if (aiming(tick) && usedAt >= aimAskedAt) {
            aimedAt = tick;
        }
    }

    /** The trigger cells go away for a tick so the client says whether it aims; at most every {@link #AIM_ASK_TICKS}. */
    public boolean askAim(long tick) {
        if (tick - aimAskAt < AIM_ASK_TICKS) {
            return false;
        }
        aimAskAt = tick;
        aimAskPending = true;
        return true;
    }

    /** The client stopped breaking its cell: if that was asked for, it is free to send a use now. */
    public void stoppedBreaking(long tick) {
        if (aimAskPending && tick - aimAskAt <= AIM_HELD_TICKS) {
            aimAskedAt = tick;
        }
        aimAskPending = false;
    }

    /** While the client may still come back from an ask, its cells stay even if it turns out to aim. */
    public boolean askingAim(long tick) {
        return tick - aimAskAt <= AIM_ASK_TICKS;
    }

    public Trigger trigger() {
        return trigger;
    }

    /** Shooting or aiming brings the gun up, out of a sprint too, which then moves at a walk. */
    public void brace(long tick) {
        bracedUntil = tick + BRACED_TICKS;
    }

    public boolean braced(long tick) {
        return tick <= bracedUntil;
    }

    /** Marks each tick whether the player looks through a scope, so its settling counts from when they raised it. */
    public void scoping(long tick, boolean scoped) {
        if (!scoped) {
            scopedSince = LONG_AGO;
        } else if (scopedSince == LONG_AGO) {
            scopedSince = tick;
        }
    }

    public OptionalInt scopedTicks(long tick) {
        return scopedSince == LONG_AGO ? OptionalInt.empty() : OptionalInt.of((int) Math.min(Integer.MAX_VALUE, tick - scopedSince));
    }

    /** A click just before the gun was ready, held over until {@code at}. */
    public void queuePull(long at) {
        queuedPull = at;
    }

    public void dropPull() {
        queuedPull = Long.MAX_VALUE;
    }

    /** Whether a held-over click is due at {@code tick}; it is due only once. */
    public boolean pullDue(long tick) {
        if (tick < queuedPull) {
            return false;
        }
        queuedPull = Long.MAX_VALUE;
        return true;
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
