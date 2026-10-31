package me.blockcraft.zombies.game;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The guns; each is a vanilla hoe that the pack draws as the gun's {@link PackModel}. */
public enum Weapon implements HotbarItem {
    PISTOL(PackModel.ZOMBIES_PISTOL),
    RIFLE(PackModel.ZOMBIES_RIFLE),
    SHOTGUN(PackModel.ZOMBIES_SHOTGUN),
    SNIPER(PackModel.ZOMBIES_SNIPER),
    FLAMETHROWER(PackModel.ZOMBIES_FLAMETHROWER);

    private final PackModel model;

    Weapon(PackModel model) {
        this.model = model;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public PackModel model() {
        return model;
    }

    /** What the armory sells and the mystery box gives; everyone starts with the pistol. */
    public static List<Weapon> buyable() {
        return Arrays.stream(values()).filter(weapon -> weapon != PISTOL).toList();
    }
}
