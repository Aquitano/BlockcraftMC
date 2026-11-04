package me.blockcraft.sternenfront.game;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;

import java.util.Locale;

/** What a class carries besides its blaster; each sits in the hotbar and shows its cooldown on the item. */
public enum Ability implements HotbarItem {
    GRENADE(PackModel.STERNENFRONT_GRENADE),
    ADRENALINE(PackModel.STERNENFRONT_ADRENALINE),
    SHIELD_WALL(PackModel.STERNENFRONT_SHIELD_EMITTER),
    JUMP_PACK(PackModel.STERNENFRONT_JUMP_PACK),
    MED_STATION(PackModel.STERNENFRONT_MED_STATION),
    SIEGE_MODE(PackModel.STERNENFRONT_SIEGE_MODE),
    MINING_CHARGE(PackModel.STERNENFRONT_MINING_CHARGE);

    private final PackModel model;

    Ability(PackModel model) {
        this.model = model;
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

    /** The client shows a cooldown on every item of a material, which is why no two abilities share one. */
    public Material material() {
        return model.fallback();
    }
}
