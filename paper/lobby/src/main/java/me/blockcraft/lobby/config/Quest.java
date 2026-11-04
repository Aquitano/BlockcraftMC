package me.blockcraft.lobby.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One of the lobby's one-off quests, from the {@code quests} section: who gives it (by skin, a player name, empty for
 * the default look), what it pays once and what it asks. A quest whose task cannot be read is left out.
 */
public record Quest(String key, String skin, int reward, Task task) {

    /** What a quest asks of the player. */
    public sealed interface Task {

        /** Find {@code count} of the lobby's lore notes. */
        record Notes(int count) implements Task {
        }

        /** Finish the lobby's parkour once. */
        record Parkour() implements Task {
        }

        /** Play a round of a game, which leaves a row in its stats {@code table}. */
        record Play(String table) implements Task {
        }
    }

    public static List<Quest> fromYaml(Map<String, Object> config) {
        var quests = new ArrayList<Quest>();
        config.forEach((key, value) -> {
            if (value instanceof Map<?, ?> quest && quest.get("task") instanceof Map<?, ?> task) {
                task(task).ifPresent(parsed -> quests.add(new Quest(String.valueOf(key),
                        String.valueOf(quest.get("skin") == null ? "" : quest.get("skin")),
                        Math.max(0, quest.get("reward") instanceof Number number ? number.intValue() : 0), parsed)));
            }
        });
        return List.copyOf(quests);
    }

    private static Optional<Task> task(Map<?, ?> task) {
        return switch (String.valueOf(task.get("type"))) {
            case "notes" -> Optional.of(new Task.Notes(Math.max(1, task.get("count") instanceof Number number ? number.intValue() : 1)));
            case "parkour" -> Optional.of(new Task.Parkour());
            case "play" -> Optional.ofNullable(task.get("table")).map(String::valueOf)
                    .filter(table -> table.matches("[A-Za-z0-9_]+")).map(Task.Play::new);
            default -> Optional.empty();
        };
    }

    public String item() {
        return "lobby.quest." + key;
    }
}
