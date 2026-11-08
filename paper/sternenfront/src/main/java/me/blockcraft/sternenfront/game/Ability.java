package me.blockcraft.sternenfront.game;

import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;

import java.util.Locale;

/** What a class carries besides its blaster, used with F and Q; {@link #material} is its cooldown's key. */
public enum Ability {
    GRENADE(PackModel.STERNENFRONT_GRENADE, AbilityIcon.GRENADE),
    ADRENALINE(PackModel.STERNENFRONT_ADRENALINE, AbilityIcon.ADRENALINE),
    SHIELD_WALL(PackModel.STERNENFRONT_SHIELD_EMITTER, AbilityIcon.SHIELD_WALL),
    JUMP_PACK(PackModel.STERNENFRONT_JUMP_PACK, AbilityIcon.JUMP_PACK),
    MED_STATION(PackModel.STERNENFRONT_MED_STATION, AbilityIcon.MED_STATION),
    SIEGE_MODE(PackModel.STERNENFRONT_SIEGE_MODE, AbilityIcon.SIEGE_MODE),
    MINING_CHARGE(PackModel.STERNENFRONT_MINING_CHARGE, AbilityIcon.MINING_CHARGE);

    private final PackModel model;
    private final AbilityIcon icon;

    Ability(PackModel model, AbilityIcon icon) {
        this.model = model;
        this.icon = icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Where the faction's own name and lore of this ability live: {@code <key>.name} and {@code <key>.lore}. */
    public String messageKey(Faction faction) {
        return "ability." + faction.key() + "." + key();
    }

    public PackModel model() {
        return model;
    }

    public AbilityIcon icon() {
        return icon;
    }

    /** The client shows a cooldown on every item of a material, which is why no two abilities share one. */
    public Material material() {
        return model.fallback();
    }
}
