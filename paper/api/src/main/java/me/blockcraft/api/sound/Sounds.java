package me.blockcraft.api.sound;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Every sound the network plays as feedback, so a click sounds the same in the lobby and in a game. Volume and
 * pitch belong to the sound, not to the call site; world sounds that belong to an effect (a gadget, a kill effect)
 * stay where they are played.
 */
public enum Sounds {

    MENU_CLICK("ui.button.click", 0.6f, 1.2f),
    MENU_DENY("entity.villager.no", 0.8f, 1.0f),
    MENU_OPEN("block.chest.open", 0.6f, 1.2f),
    TELEPORT("entity.enderman.teleport", 0.8f, 1.0f),
    COUNTDOWN_TICK("block.note_block.pling", 1.0f, 1.0f),
    COUNTDOWN_GO("block.note_block.pling", 1.0f, 2.0f),
    COINS("entity.experience_orb.pickup", 0.7f, 1.4f),
    PURCHASE("entity.player.levelup", 0.8f, 1.4f),
    KILL("entity.player.attack_crit", 1.0f, 1.0f),
    WIN("ui.toast.challenge_complete", 1.0f, 1.0f),
    LOSE("entity.blaze.death", 0.8f, 1.0f),
    LEVEL_UP("entity.player.levelup", 1.0f, 1.0f),
    JOIN("block.note_block.bell", 0.6f, 1.6f),
    NOTIFY("block.note_block.pling", 0.8f, 1.8f),
    SECRET("entity.ender_dragon.growl", 0.6f, 1.2f);

    private final Sound sound;

    Sounds(String key, float volume, float pitch) {
        this.sound = Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
    }

    /** Silent for players who turned the network's feedback sounds off in their settings. */
    public void play(Player player) {
        if (BlockcraftApi.get().settings().enabled(player, PlayerSetting.SOUNDS)) {
            player.playSound(sound);
        }
    }

    public void playToAll() {
        Bukkit.getOnlinePlayers().forEach(this::play);
    }

    public Sound sound() {
        return sound;
    }
}
