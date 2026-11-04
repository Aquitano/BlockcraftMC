package me.blockcraft.sternenfront.game;

import java.util.List;
import java.util.Locale;

/**
 * The four roles a player deploys as: a blaster and one or two abilities, the first on F, the second on Q. Both
 * factions fill the same roles, under their own names, and may carry different abilities in them.
 */
public enum CombatClass {
    SOLDIER(Blaster.CARBINE, List.of(Ability.GRENADE, Ability.ADRENALINE)),
    HEAVY(Blaster.REPEATER, List.of(Ability.SHIELD_WALL, Ability.MINING_CHARGE), List.of(Ability.SHIELD_WALL, Ability.SIEGE_MODE)),
    SCOUT(Blaster.LONG_RIFLE, List.of(Ability.JUMP_PACK)),
    MEDIC(Blaster.PISTOL, List.of(Ability.MED_STATION));

    private final Blaster blaster;
    private final List<Ability> kolonien;
    private final List<Ability> hegemonie;

    CombatClass(Blaster blaster, List<Ability> abilities) {
        this(blaster, abilities, abilities);
    }

    CombatClass(Blaster blaster, List<Ability> kolonien, List<Ability> hegemonie) {
        this.blaster = blaster;
        this.kolonien = kolonien;
        this.hegemonie = hegemonie;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Where the faction's own name and lore of this role live: {@code <key>.name} and {@code <key>.lore}. */
    public String messageKey(Faction faction) {
        return "class." + faction.key() + "." + key();
    }

    public Blaster blaster() {
        return blaster;
    }

    public List<Ability> abilities(Faction faction) {
        return faction == Faction.KOLONIEN ? kolonien : hegemonie;
    }
}
