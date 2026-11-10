package me.blockcraft.api.dialogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Dialogue(String first, Map<String, Line> lines) {
    public record Choice(String text, String next) {}
    public record Line(String speaker, String text, Portrait portrait, String next, List<Choice> choices) {
        public Line {
            choices = List.copyOf(choices);
        }
    }
    public enum Portrait {
        PILOT, PILOT_HAPPY, PILOT_WORRIED, DROID, DROID_ALERT
    }
    public record Loaded(Dialogue dialogue, List<String> problems) {
        public boolean valid() {
            return problems.isEmpty();
        }
    }

    public Dialogue {
        lines = Map.copyOf(lines);
    }

    public List<String> problems() {
        var problems = new ArrayList<String>();
        if (!lines.containsKey(first)) problems.add("Missing first dialogue line: " + first);
        if (lines.size() > 100) problems.add("A dialogue supports at most 100 lines");
        lines.forEach((id, line) -> {
            if (line.speaker().isBlank() || line.text().isBlank()) problems.add("Missing message key in " + id);
            if (line.choices().size() > 3) problems.add("At most three choices per line: " + id);
            if (!line.next().isBlank() && !line.choices().isEmpty()) problems.add("Line has both next and choices: " + id);
            if (!line.next().isBlank() && !lines.containsKey(line.next())) problems.add("Missing target: " + line.next());
            for (var choice : line.choices()) {
                if (choice.text().isBlank()) problems.add("Missing choice message key in " + id);
                if (!choice.next().isBlank() && !lines.containsKey(choice.next())) problems.add("Missing target: " + choice.next());
            }
        });
        return List.copyOf(problems);
    }

    public static Loaded load(Map<String, Object> config) {
        var lines = new LinkedHashMap<String, Line>();
        var problems = new ArrayList<String>();
        if (config.get("lines") instanceof Map<?, ?> entries) {
            entries.forEach((key, value) -> {
                if (!(value instanceof Map<?, ?> fields)) {
                    problems.add("Expected dialogue line object: " + key);
                    return;
                }
                Portrait portrait;
                try {
                    portrait = Portrait.valueOf(string(fields, "portrait", "PILOT"));
                } catch (IllegalArgumentException ignored) {
                    problems.add("Unknown portrait in " + key);
                    portrait = Portrait.PILOT;
                }
                var choices = new ArrayList<Choice>();
                if (fields.get("choices") instanceof List<?> options) {
                    for (var option : options) {
                        if (option instanceof Map<?, ?> choice) {
                            choices.add(new Choice(string(choice, "text", ""), string(choice, "next", "")));
                        } else {
                            problems.add("Expected choice object in " + key);
                        }
                    }
                }
                lines.put(String.valueOf(key), new Line(string(fields, "speaker", ""), string(fields, "text", ""),
                        portrait, string(fields, "next", ""), choices));
            });
        }
        var dialogue = new Dialogue(string(config, "first", ""), lines);
        problems.addAll(dialogue.problems());
        return new Loaded(dialogue, List.copyOf(problems));
    }

    private static String string(Map<?, ?> fields, String key, String fallback) {
        return fields.get(key) instanceof String value ? value : fallback;
    }
}
