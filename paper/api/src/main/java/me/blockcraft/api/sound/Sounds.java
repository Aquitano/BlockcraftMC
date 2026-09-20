package me.blockcraft.api.sound;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.sound.FeedbackSound;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public enum Sounds {

    MENU_CLICK(FeedbackSound.MENU_CLICK),
    MENU_DENY(FeedbackSound.MENU_DENY),
    MENU_OPEN(FeedbackSound.MENU_OPEN),
    TELEPORT(FeedbackSound.TELEPORT),
    COUNTDOWN_TICK(FeedbackSound.COUNTDOWN_TICK),
    COUNTDOWN_GO(FeedbackSound.COUNTDOWN_GO),
    COINS(FeedbackSound.COINS),
    PURCHASE(FeedbackSound.PURCHASE),
    KILL(FeedbackSound.KILL),
    STREAK(FeedbackSound.STREAK),
    WIN(FeedbackSound.WIN),
    LOSE(FeedbackSound.LOSE),
    LEVEL_UP(FeedbackSound.LEVEL_UP),
    ACHIEVEMENT(FeedbackSound.ACHIEVEMENT),
    JOIN(FeedbackSound.JOIN),
    QUICKSG_JOIN(FeedbackSound.QUICKSG_JOIN),
    RUSH_JOIN(FeedbackSound.RUSH_JOIN),
    DUELL_JOIN(FeedbackSound.DUELL_JOIN),
    NOTIFY(FeedbackSound.NOTIFY),
    SECRET(FeedbackSound.SECRET),
    EQUIP(FeedbackSound.EQUIP),
    HEAL(FeedbackSound.HEAL),
    PVP_ENABLED(FeedbackSound.PVP_ENABLED);

    private static final int COUNTDOWN_FROM = 5;
    private static final float COUNTDOWN_PITCH_STEP = 0.15f;
    private final FeedbackSound feedback;

    Sounds(FeedbackSound feedback) {
        this.feedback = feedback;
    }

    public static void countdownTick(int secondsLeft) {
        Bukkit.getOnlinePlayers().forEach(player -> countdownTick(player, secondsLeft));
    }

    public static void countdownTick(Player player, int secondsLeft) {
        var base = COUNTDOWN_TICK.sound();
        float pitch = base.pitch() + (COUNTDOWN_FROM - Math.clamp(secondsLeft, 1, COUNTDOWN_FROM)) * COUNTDOWN_PITCH_STEP;
        playIfEnabled(player, Sound.sound(base.name(), base.source(), base.volume(), pitch));
    }

    public void play(Player player) {
        playIfEnabled(player, feedback.sound(BlockcraftApi.get().resourcePack().applied(player)));
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
        return feedback.sound();
    }
}
