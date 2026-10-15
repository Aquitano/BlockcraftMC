package me.blockcraft.bedwars.perk;

import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The four kinds of perks, each one remembered setting. The menu offers every choice but the standard one, which
 * resetting the group returns to; a group without a standard plays nothing until something is chosen.
 */
public enum PerkGroup {
    STICK(PlayerSetting.BEDWARS_STICK, Material.STICK, choices(StickSkin.values(), StickSkin.STICK)),
    BED_SOUND(PlayerSetting.BEDWARS_BED_SOUND, Material.RED_BED, choices(BedSound.values(), BedSound.IRON_GOLEM)),
    KILL_SOUND(PlayerSetting.BEDWARS_KILL_SOUND, Material.NOTE_BLOCK, List.of(KillSound.values())),
    ARROW_TRAIL(PlayerSetting.BEDWARS_ARROW_TRAIL, Material.BOW, List.of(ArrowTrail.values()));

    private final PlayerSetting setting;
    private final Material icon;
    private final List<Perk> choices;

    PerkGroup(PlayerSetting setting, Material icon, List<Perk> choices) {
        this.setting = setting;
        this.icon = icon;
        this.choices = choices;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public PlayerSetting setting() {
        return setting;
    }

    public Material icon() {
        return icon;
    }

    public List<Perk> choices() {
        return choices;
    }

    private static List<Perk> choices(Perk[] all, Perk standard) {
        return Arrays.stream(all).filter(perk -> perk != standard).toList();
    }
}
