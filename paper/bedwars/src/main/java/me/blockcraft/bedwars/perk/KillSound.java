package me.blockcraft.bedwars.perk;

import me.blockcraft.api.sound.Sounds;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a player hears when they kill someone; without a choice they hear nothing extra. */
public enum KillSound implements Perk {
    PISTON(Material.PISTON, Sound.BLOCK_PISTON_EXTEND),
    FIREWORK(Material.FIREWORK_ROCKET, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH),
    GHAST(Material.GHAST_SPAWN_EGG, Sound.ENTITY_GHAST_DEATH),
    LEVEL_UP(Material.EXPERIENCE_BOTTLE, Sound.ENTITY_PLAYER_LEVELUP),
    WOLF(Material.BONE, Sound.ENTITY_WOLF_AMBIENT),
    SKELETON(Material.SKELETON_SKULL, Sound.ENTITY_SKELETON_HURT),
    TNT(Material.TNT, Sound.ENTITY_GENERIC_EXPLODE),
    CURE(Material.GOLDEN_APPLE, Sound.ENTITY_ZOMBIE_VILLAGER_CURE);

    private final Material icon;
    private final Sound sound;

    KillSound(Material icon, Sound sound) {
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
        Sounds.playChosen(player, sound, 1.0f);
    }

    public static Optional<KillSound> byKey(String key) {
        return Arrays.stream(values()).filter(sound -> sound.key().equals(key)).findFirst();
    }
}
