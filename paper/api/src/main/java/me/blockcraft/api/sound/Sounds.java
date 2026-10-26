package me.blockcraft.api.sound;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.sound.FeedbackSound;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    DOUBLE_JUMP(FeedbackSound.DOUBLE_JUMP),
    JUMP_PAD(FeedbackSound.JUMP_PAD),
    GRAPPLE(FeedbackSound.GRAPPLE),
    PAINT_THROW(FeedbackSound.PAINT_THROW),
    EQUIP(FeedbackSound.EQUIP),
    HEAL(FeedbackSound.HEAL),
    PVP_ENABLED(FeedbackSound.PVP_ENABLED),
    NOTE_BIT(FeedbackSound.NOTE_BIT),
    NOTE_PLING(FeedbackSound.NOTE_PLING),
    NOTE_BASS(FeedbackSound.NOTE_BASS),
    SHOP_TALK(FeedbackSound.SHOP_TALK),
    SHOP_HURT(FeedbackSound.SHOP_HURT),
    PLAYER_HURT(FeedbackSound.PLAYER_HURT),
    EASTER_EGG(FeedbackSound.EASTER_EGG),
    ADVENT_DOOR(FeedbackSound.ADVENT_DOOR),
    TRICK(FeedbackSound.TRICK),
    SCARE(FeedbackSound.SCARE),
    SLEIGH_BELLS(FeedbackSound.SLEIGH_BELLS),
    CHAINS(FeedbackSound.CHAINS),
    HEROBRINE(FeedbackSound.HEROBRINE);

    private static final int COUNTDOWN_FROM = 5;
    private static final float COUNTDOWN_PITCH_STEP = 0.15f;
    private static final int NOTE_BLOCK_SEMITONES = 24;
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

    /** A note on the note block's own scale, {@code semitone} 0 (F#3) to 24 (F#5); this sound's pitch is ignored. */
    public void note(Player player, int semitone) {
        playIfEnabled(player, noteSound(semitone));
    }

    /** The same note, heard from {@code at} the way a world sound is. */
    public void note(Player player, int semitone, Location at) {
        if (BlockcraftApi.get().settings().enabled(player, PlayerSetting.SOUNDS)) {
            player.playSound(noteSound(semitone), at.getX(), at.getY(), at.getZ());
        }
    }

    private Sound noteSound(int semitone) {
        var base = sound();
        float pitch = (float) Math.pow(2, (Math.clamp(semitone, 0, NOTE_BLOCK_SEMITONES) - 12) / 12.0);
        return Sound.sound(base.name(), base.source(), base.volume(), pitch);
    }

    public void play(Player player) {
        playIfEnabled(player, feedback.sound(BlockcraftApi.get().resourcePack().applied(player)));
    }

    /** A sound the player chose, such as a perk's, plays only for players who keep sounds on, like every feedback sound. */
    public static void playChosen(Player player, Sound.Type sound, float pitch) {
        playIfEnabled(player, Sound.sound(sound, Sound.Source.MASTER, 1.0f, pitch));
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
