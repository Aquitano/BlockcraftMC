package me.blockcraft.ttt.task.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/** The chore games by name, which a puzzle chore gives as its game. */
public final class ChoreGames {

    public static final Set<String> NAMES = Set.of("organ", "potion", "chronicle", "ledger", "millwork", "forge");

    private ChoreGames() {
    }

    public static ChoreGame of(String game, Plugin plugin, Messages messages, Component title, Runnable solved) {
        return switch (game) {
            case "organ" -> new OrganGame(plugin, messages, title, solved);
            case "potion" -> new PotionGame(plugin, messages, title, solved);
            case "chronicle" -> new ChronicleGame(plugin, messages, title, solved);
            case "ledger" -> new LedgerGame(plugin, messages, title, solved);
            case "millwork" -> new MillworkGame(plugin, messages, title, solved);
            case "forge" -> new ForgeGame(plugin, messages, title, solved);
            default -> throw new IllegalArgumentException("No chore game " + game);
        };
    }
}
