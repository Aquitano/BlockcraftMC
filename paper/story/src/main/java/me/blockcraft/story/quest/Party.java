package me.blockcraft.story.quest;

import me.blockcraft.story.game.Choice;
import me.blockcraft.story.game.Stage;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * The party's shared story state as BetonQuest sees it. BetonQuest keeps state per player, so its conditions read
 * this instead, and every member, including one who rejoins, sees the same stage and decision.
 */
public interface Party {

    Stage stage();

    Optional<Choice> choice();

    boolean voted(UUID player);

    void advance(Stage stage);

    void vote(Player player, Choice choice);

    void consoleReached(ConsoleObjective console);
}
