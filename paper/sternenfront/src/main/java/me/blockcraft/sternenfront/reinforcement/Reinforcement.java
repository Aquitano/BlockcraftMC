package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.Faction;

import java.util.List;
import java.util.Locale;

public enum Reinforcement {
    ARC_TROOPER(Faction.KOLONIEN, Blaster.ARC_REPEATER, List.of(Ability.SHIELD_WALL, Ability.ADRENALINE)),
    COMMANDO_DROID(Faction.HEGEMONIE, Blaster.COMMANDO_CARBINE, List.of(Ability.JUMP_PACK, Ability.GRENADE));

    private final Faction faction;
    private final Blaster blaster;
    private final List<Ability> abilities;

    Reinforcement(Faction faction, Blaster blaster, List<Ability> abilities) {
        this.faction = faction;
        this.blaster = blaster;
        this.abilities = abilities;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public Faction faction() {
        return faction;
    }

    public Blaster blaster() {
        return blaster;
    }

    public PackModel helmet() {
        return switch (this) {
            case ARC_TROOPER -> PackModel.STERNENFRONT_HELMET_ARC;
            case COMMANDO_DROID -> PackModel.STERNENFRONT_HELMET_COMMANDO;
        };
    }

    public List<Ability> abilities() {
        return abilities;
    }

    public static Reinforcement of(Faction faction) {
        return switch (faction) {
            case KOLONIEN -> ARC_TROOPER;
            case HEGEMONIE -> COMMANDO_DROID;
        };
    }
}
