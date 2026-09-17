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
    STREAK("item.trident.thunder", 0.6f, 1.3f),
    WIN("ui.toast.challenge_complete", 1.0f, 1.0f),
    LOSE("block.note_block.bass", 0.5f, 0.8f),
    LEVEL_UP("entity.player.levelup", 1.0f, 1.0f),
    JOIN("block.note_block.bell", 0.6f, 1.6f),
    QUICKSG_JOIN("item.crossbow.loading_end", 0.5f, 1.1f),
    RUSH_JOIN("block.amethyst_block.chime", 0.5f, 1.2f),
    DUELL_JOIN("block.anvil.place", 0.25f, 1.5f),
    NOTIFY("block.note_block.pling", 0.8f, 1.8f),
    SECRET("entity.ender_dragon.growl", 0.6f, 1.2f);

    private static final int COUNTDOWN_FROM = 5;
    private static final float COUNTDOWN_PITCH_STEP = 0.15f;

    private final Key key;
    private final float volume;
    private final Sound sound;

    Sounds(String key, float volume, float pitch) {
        this.key = Key.key(key);
        this.volume = volume;
        this.sound = Sound.sound(this.key, Sound.Source.MASTER, volume, pitch);
    }

    /** The last seconds before a start: the closer to zero, the higher the tick. */
    public static void countdownTick(int secondsLeft) {
        Bukkit.getOnlinePlayers().forEach(player -> countdownTick(player, secondsLeft));
    }

    public static void countdownTick(Player player, int secondsLeft) {
        playIfEnabled(player, COUNTDOWN_TICK.at(countdownPitch(secondsLeft)));
    }

    private static float countdownPitch(int secondsLeft) {
        return COUNTDOWN_TICK.sound.pitch() + (COUNTDOWN_FROM - Math.clamp(secondsLeft, 1, COUNTDOWN_FROM)) * COUNTDOWN_PITCH_STEP;
    }

    private Sound at(float pitch) {
        return Sound.sound(key, Sound.Source.MASTER, volume, pitch);
    }

    /** Silent for players who turned the network's feedback sounds off in their settings. */
    public void play(Player player) {
        playIfEnabled(player, sound);
    }

    private static void playIfEnabled(Player player, Sound sound) {
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
