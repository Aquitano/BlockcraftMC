package me.blockcraft.sternenfront.combat;

public final class Guard {

    public enum Result { NONE, BLOCK, PARRY, BROKEN }

    private final CombatConfig config;
    private double strength;
    private long raisedAt;
    private long recoverAt;
    private long brokenUntil;
    private boolean raised;

    public Guard(CombatConfig config) {
        this.config = config;
        strength = config.guardCapacity();
    }

    public void tick(long tick, boolean holding) {
        boolean next = holding && tick >= brokenUntil && strength > 0;
        if (next && !raised) {
            raisedAt = tick;
        }
        raised = next;
        if (!raised && tick >= recoverAt && tick >= brokenUntil) {
            strength = Math.min(config.guardCapacity(), strength + config.guardRecovery());
        }
    }

    public Result hit(long tick, double damage) {
        if (!raised || tick < brokenUntil) {
            return Result.NONE;
        }
        recoverAt = tick + config.recoveryDelay();
        boolean parry = tick - raisedAt < config.parryTicks();
        strength = Math.max(0, strength - damage * config.guardCost() * (parry ? 0.5 : 1));
        if (strength == 0) {
            raised = false;
            brokenUntil = tick + config.breakTicks();
            return Result.BROKEN;
        }
        return parry ? Result.PARRY : Result.BLOCK;
    }

    public double level() {
        return strength / config.guardCapacity();
    }

    public boolean raised() {
        return raised;
    }

    public boolean broken(long tick) {
        return tick < brokenUntil;
    }
}
