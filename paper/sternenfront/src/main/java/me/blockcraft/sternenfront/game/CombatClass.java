package me.blockcraft.sternenfront.game;

import java.util.List;
import java.util.Locale;

/** What a player deploys as: a blaster and one or two abilities, the first on F, the second on Q. */
public enum CombatClass {
    SOLDIER(Blaster.CARBINE, List.of(Ability.GRENADE, Ability.ADRENALINE)),
    HEAVY(Blaster.REPEATER, List.of(Ability.SHIELD_WALL)),
    SCOUT(Blaster.LONG_RIFLE, List.of(Ability.JUMP_PACK)),
    MEDIC(Blaster.PISTOL, List.of(Ability.MED_STATION));

    private final Blaster blaster;
    private final List<Ability> abilities;

    CombatClass(Blaster blaster, List<Ability> abilities) {
        this.blaster = blaster;
        this.abilities = abilities;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Blaster blaster() {
        return blaster;
    }

    public List<Ability> abilities() {
        return abilities;
    }
}
