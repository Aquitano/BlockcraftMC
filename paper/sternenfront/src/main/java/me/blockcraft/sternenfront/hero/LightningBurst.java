package me.blockcraft.sternenfront.hero;

final class LightningBurst {

    private final HeroConfig.Lightning rules;
    private final long until;
    private long next;

    LightningBurst(HeroConfig.Lightning rules, long tick) {
        this.rules = rules;
        until = tick + rules.ticks();
        next = tick;
    }

    boolean active(long tick) {
        return tick < until;
    }

    boolean pulse(long tick) {
        if (!active(tick) || tick < next) {
            return false;
        }
        next = tick + rules.pulseTicks();
        return true;
    }

    double damage() {
        return rules.damagePerPulse();
    }
}
