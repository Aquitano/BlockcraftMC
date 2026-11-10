package me.blockcraft.bedwars.perk;

import me.blockcraft.api.sound.Sounds;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a player hears whenever any team's bed is broken. */
public enum BedSound implements Perk {
    IRON_GOLEM(Material.IRON_BLOCK, Sound.ENTITY_IRON_GOLEM_DEATH),
    ENDER_DRAGON(Material.DRAGON_EGG, Sound.ENTITY_ENDER_DRAGON_GROWL),
    WITHER_SPAWN(Material.NETHER_STAR, Sound.ENTITY_WITHER_SPAWN),
    SKELETON(Material.BONE, Sound.ENTITY_SKELETON_DEATH),
    WITHER_DEATH(Material.WITHER_SKELETON_SKULL, Sound.ENTITY_WITHER_DEATH);

    private static final float PITCH = 0.8f;

    private final Material icon;
    private final Sound sound;

    BedSound(Material icon, Sound sound) {
        this.icon = icon;
        this.sound = sound;
    }

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public ItemStack icon() {
        return ItemStack.of(icon);
    }

    public void play(Player player) {
        Sounds.playChosen(player, sound, PITCH);
    }

    public static Optional<BedSound> byKey(String key) {
        return Arrays.stream(values()).filter(sound -> sound.key().equals(key)).findFirst();
    }
}
