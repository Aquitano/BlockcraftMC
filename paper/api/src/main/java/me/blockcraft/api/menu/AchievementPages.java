package me.blockcraft.api.menu;

import me.blockcraft.api.achievement.Achievement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Keeps a game's entries together in rows without dropping entries that outgrow a chest page. */
final class AchievementPages {

    static final int COLUMNS = 9;
    static final int ENTRIES_PER_ROW = COLUMNS - 1;
    static final int ROWS_PER_PAGE = 5;

    record Row(String game, List<Achievement> entries) {
        Row {
            entries = List.copyOf(entries);
        }
    }

    private final List<Row> rows;

    AchievementPages(List<Achievement> catalog) {
        var games = new LinkedHashMap<String, List<Achievement>>();
        catalog.forEach(entry -> games.computeIfAbsent(entry.game(), ignored -> new ArrayList<>()).add(entry));
        var rows = new ArrayList<Row>();
        games.forEach((game, entries) -> {
            for (int start = 0; start < entries.size(); start += ENTRIES_PER_ROW) {
                rows.add(new Row(game, entries.subList(start, Math.min(start + ENTRIES_PER_ROW, entries.size()))));
            }
        });
        this.rows = List.copyOf(rows);
    }

    int inventoryRows() {
        return Math.min(ROWS_PER_PAGE, rows.size()) + 1;
    }

    int count() {
        return Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    List<Row> page(int page) {
        if (page < 0 || page >= count()) {
            throw new IndexOutOfBoundsException(page);
        }
        int start = page * ROWS_PER_PAGE;
        return rows.subList(start, Math.min(start + ROWS_PER_PAGE, rows.size()));
    }
}
