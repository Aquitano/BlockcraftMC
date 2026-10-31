package me.blockcraft.zombies.game;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;

/** Small, quick and frail; it comes in packs, and its size alone makes it hard to hit. */
final class Crawler implements Ability {

    private final ZombiesConfig.Crawler config;

    Crawler(ZombiesConfig.Crawler config) {
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_CRAWLER_EYES.item().build());
    }

    @Override
    public int pack() {
        return config.pack();
    }
}
