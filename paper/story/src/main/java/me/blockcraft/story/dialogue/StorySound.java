package me.blockcraft.story.dialogue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;

/** The story's own sounds, which {@code resourcepack/StorySounds.py} synthesizes into the pack as {@code story.*}. */
public enum StorySound {
    DIALOGUE_OPEN(0.7f), DIALOGUE_TYPE(0.35f), DIALOGUE_SELECT(0.5f), DIALOGUE_CHOOSE(0.7f), DIALOGUE_ADVANCE(0.5f),
    DIALOGUE_CLOSE(0.5f), OBJECTIVE_START(0.9f), VOTE_CAST(0.7f), VOTE_DECIDED(0.9f);

    private final Key key;
    private final float volume;

    StorySound(float volume) {
        this.key = Key.key("blockcraft", "story." + name().toLowerCase(Locale.ROOT).replaceFirst("_", "."));
        this.volume = volume;
    }

    public Key key() {
        return key;
    }

    public void play(Player player) {
        play(player, 1);
    }

    public void play(Player player, float pitch) {
        player.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch), Sound.Emitter.self());
    }

    public void play(Location at) {
        at.getWorld().playSound(Sound.sound(key, Sound.Source.AMBIENT, volume, 1), at.getX(), at.getY(), at.getZ());
    }
}
