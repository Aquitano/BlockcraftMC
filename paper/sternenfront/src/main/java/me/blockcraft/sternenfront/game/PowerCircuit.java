package me.blockcraft.sternenfront.game;

final class PowerCircuit {
    private boolean powered = true;
    private long overriddenUntil;

    boolean powered() { return powered; }
    boolean secure(long tick) { return powered && tick >= overriddenUntil; }
    void power(boolean enabled) { powered = enabled; }
    void console(boolean friendly, long tick, long duration) { overriddenUntil = friendly ? 0 : tick + duration; }
    void reset() { powered = true; overriddenUntil = 0; }
}
