package me.blockcraft.api.cosmetic;

import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;

/** The hooks games call; they play whatever the player chose in the lobby and nothing otherwise. */
public final class Effects {

    private final PlayerSettings settings;

    public Effects(PlayerSettings settings) {
        this.settings = settings;
    }

    public void onKill(Player killer, Player victim) {
        settings.get(killer, PlayerSetting.KILL_EFFECT).flatMap(KillEffect::byKey).ifPresent(effect -> effect.play(victim));
    }

    public void onWin(Player winner) {
        settings.get(winner, PlayerSetting.WIN_EFFECT).flatMap(WinEffect::byKey).ifPresent(effect -> effect.play(winner));
    }
}
