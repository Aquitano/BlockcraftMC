package me.blockcraft.story.quest;

import me.blockcraft.story.sound.Soundtrack;
import me.blockcraft.story.state.Facts;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The party's shared story as BetonQuest sees it. BetonQuest keeps state per player, so its conditions read these
 * facts instead, and every member, including one who rejoins, sees the same stage and decisions. Each {@code cause}
 * names who asked, for the trace.
 */
public interface Party {

    Facts facts();

    /** Whether {@code player} plays the mission; a spectator's objectives must not move the party on. */
    boolean playing(UUID player);

    /** The stage the party is in, empty before the mission starts. */
    String stage();

    boolean voted(UUID player, String topic);

    /** Moves the party on to {@code stage}; a stage it has already passed is ignored. */
    void advance(String stage, String cause);

    void vote(Player player, String topic, String choice);

    /** Plays a cutscene to the party, then runs the package's {@code then} actions once. */
    void scene(String name, List<String> then, String cause);

    /** Runs the package's {@code actions} once after {@code ticks}, unless a jump or the round's end comes first. */
    void after(int ticks, List<String> actions, String cause);

    /** Starts {@code track} on the channel, or ends it with "stop". */
    void soundtrack(Soundtrack.Channel channel, String track, String cause);

    void sound(Key sound, float volume, float pitch, Optional<Location> at);

    /** Ends the mission with {@code ending}'s title, summary and rewards. */
    void end(String ending, String cause);

    /** {@code member} stands at the console; a console the party has held stays held until a jump. */
    void consoleReached(ConsoleObjective console, Player member);
}
