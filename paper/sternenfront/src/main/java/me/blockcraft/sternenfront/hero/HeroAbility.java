package me.blockcraft.sternenfront.hero;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;

import java.util.Locale;

/** What heroes do besides fighting with their weapon; like a class's abilities, F uses the first and Q the second. */
public enum HeroAbility implements HotbarItem {
    FORCE_PUSH(PackModel.STERNENFRONT_FORCE_PUSH),
    SABER_THROW(PackModel.STERNENFRONT_SABER_THROW),
    FORCE_GRIP(PackModel.STERNENFRONT_FORCE_GRIP),
    JETPACK(PackModel.STERNENFRONT_JETPACK),
    GRAPPLE(PackModel.STERNENFRONT_GRAPPLE),
    ORBITAL_STRIKE(PackModel.STERNENFRONT_ORBITAL_BEACON),
    RALLY(PackModel.STERNENFRONT_RALLY);

    private final PackModel model;

    HeroAbility(PackModel model) {
        this.model = model;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public PackModel model() {
        return model;
    }

    /** The client shows a cooldown on every item of a material, so no two of these, nor a class ability, share one. */
    public Material material() {
        return model.fallback();
    }
}
