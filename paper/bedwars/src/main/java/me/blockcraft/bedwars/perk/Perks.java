package me.blockcraft.bedwars.perk;

import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Every player's perks, read from their settings, which the network remembers between rounds. */
public final class Perks {

    private final PlayerSettings settings;

    public Perks(PlayerSettings settings) {
        this.settings = settings;
    }

    public StickSkin stick(Player player) {
        return chosen(player, PlayerSetting.WEAPON_STYLE, StickSkin::byKey).orElse(StickSkin.STICK);
    }

    public BedSound bedSound(Player player) {
        return chosen(player, PlayerSetting.BEDWARS_BED_SOUND, BedSound::byKey).orElse(BedSound.IRON_GOLEM);
    }

    public Optional<KillSound> killSound(Player player) {
        return chosen(player, PlayerSetting.BEDWARS_KILL_SOUND, KillSound::byKey);
    }

    public Optional<ArrowTrail> arrowTrail(Player player) {
        return chosen(player, PlayerSetting.BEDWARS_ARROW_TRAIL, ArrowTrail::byKey);
    }

    public boolean isSelected(Player player, PerkGroup group, Perk perk) {
        return settings.get(player, group.setting()).filter(perk.key()::equals).isPresent();
    }

    public CompletableFuture<Boolean> select(Player player, PerkGroup group, Perk perk) {
        return settings.set(player, group.setting(), perk.key());
    }

    public CompletableFuture<Boolean> reset(Player player, PerkGroup group) {
        return settings.clear(player, group.setting());
    }

    private <P extends Perk> Optional<P> chosen(Player player, PlayerSetting setting, Function<String, Optional<P>> byKey) {
        return settings.get(player, setting).flatMap(byKey);
    }
}
