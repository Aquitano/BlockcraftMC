package me.blockcraft.zombies.game;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Survivor {

    public enum Life { FIGHTING, DOWNED, OUT }

    private Life life = Life.FIGHTING;
    private int gold;
    private int bleedAt;
    private int reviveProgress;
    private int revives;
    private int armor;
    private final Map<Weapon, Ammo> weapons = new LinkedHashMap<>();
    private final Set<Perk> perks = EnumSet.noneOf(Perk.class);

    public Survivor(int gold) { this.gold = gold; }
    public Life life() { return life; }
    public int gold() { return gold; }
    public int bleedAt() { return bleedAt; }
    public int reviveProgress() { return reviveProgress; }
    public int revives() { return revives; }
    public int armor() { return armor; }
    public Map<Weapon, Ammo> weapons() { return weapons; }
    public Set<Perk> perks() { return perks; }
    public boolean fighting() { return life == Life.FIGHTING; }
    public void addGold(int amount) { gold += amount; }
    public void helped() { revives++; }
    public void armor(int tier) { armor = tier; }

    public boolean spend(int price) {
        if (price < 0 || gold < price) {
            return false;
        }
        gold -= price;
        return true;
    }

    public boolean down(int tick, int bleedTicks) {
        if (!fighting()) {
            return false;
        }
        life = Life.DOWNED;
        bleedAt = tick + bleedTicks;
        reviveProgress = 0;
        weapons.values().forEach(Ammo::cancelReload);
        return true;
    }

    public boolean revive(int progress, int required) {
        if (life != Life.DOWNED) {
            return false;
        }
        reviveProgress = progress == 0 ? 0 : reviveProgress + progress;
        if (reviveProgress < required) {
            return false;
        }
        recover();
        return true;
    }

    public boolean bleed(int tick) {
        if (life != Life.DOWNED || tick < bleedAt) {
            return false;
        }
        return die();
    }

    public boolean die() {
        if (life == Life.OUT) {
            return false;
        }
        life = Life.OUT;
        perks.clear();
        reviveProgress = 0;
        return true;
    }

    public void recover() {
        life = Life.FIGHTING;
        bleedAt = 0;
        reviveProgress = 0;
    }
}
