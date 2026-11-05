package me.blockcraft.api.dialogue;

import java.util.List;
import java.util.function.Function;

final class DialogueSession {
    private final Dialogue dialogue;
    private final Function<String, String> text;
    private String lineId;
    private List<List<String>> pages;
    private int page;
    private int visible;
    private int choice;
    private boolean finished;

    DialogueSession(Dialogue dialogue, Function<String, String> text) {
        this.dialogue = dialogue;
        this.text = text;
        enter(dialogue.first());
    }

    Dialogue.Line line() { return dialogue.lines().get(lineId); }
    String lineId() { return lineId; }
    int choice() { return choice; }
    int page() { return page; }
    int pageCount() { return pages.size(); }
    boolean finished() { return finished; }
    boolean revealed() { return visible >= length(); }
    boolean choosing() { return revealed() && page == pages.size() - 1 && !line().choices().isEmpty(); }

    void reveal(int characters) {
        visible = Math.min(length(), visible + characters);
    }

    List<String> visibleLines() {
        int remaining = visible;
        var lines = new java.util.ArrayList<String>();
        for (String line : pages.get(page)) {
            int count = Math.min(line.codePointCount(0, line.length()), remaining);
            lines.add(line.substring(0, line.offsetByCodePoints(0, count)));
            remaining -= count;
        }
        return List.copyOf(lines);
    }

    void select(int delta) {
        if (choosing()) choice = Math.floorMod(choice + delta, line().choices().size());
    }

    void advance() {
        if (finished) return;
        if (!revealed()) {
            visible = length();
        } else if (page + 1 < pages.size()) {
            page++;
            visible = 0;
        } else {
            String next = line().choices().isEmpty() ? line().next() : line().choices().get(choice).next();
            if (next.isBlank()) finished = true;
            else enter(next);
        }
    }

    private int length() {
        return pages.get(page).stream().mapToInt(value -> value.codePointCount(0, value.length())).sum();
    }

    private void enter(String id) {
        lineId = id;
        pages = DialogueText.pages(text.apply(line().text()), 222);
        page = 0;
        visible = 0;
        choice = 0;
    }
}
