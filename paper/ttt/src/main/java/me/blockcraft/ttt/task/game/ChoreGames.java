package me.blockcraft.ttt.task.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

/** Which game each played chore of the map is, by the chore's key. */
public final class ChoreGames {

    private ChoreGames() {
    }

    public static ChoreGame of(String chore, Plugin plugin, Messages messages, Component title, Runnable solved) {
        return switch (chore) {
            case "organ" -> new OrganGame(plugin, messages, title, solved);
            case "potion" -> new PotionGame(plugin, messages, title, solved);
            case "chronicle" -> new ChronicleGame(plugin, messages, title, solved);
            case "ledger" -> new LedgerGame(plugin, messages, title, solved);
            case "millwork" -> new MillworkGame(plugin, messages, title, solved);
            case "forge" -> new ForgeGame(plugin, messages, title, solved);
            default -> throw new IllegalArgumentException("No game for the chore " + chore);
        };
    }
}
