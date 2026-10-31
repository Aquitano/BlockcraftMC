package me.blockcraft.api.menu;

import me.blockcraft.api.achievement.Achievement;
import me.blockcraft.api.achievement.Achievements;
import me.blockcraft.api.achievement.Metric;
import me.blockcraft.common.config.YamlFile;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AchievementPagesTest {

    @Test
    public void everyConfiguredAchievementIsVisibleExactlyOnce() throws Exception {
        List<Achievement> catalog;
        try (var config = getClass().getResourceAsStream("/config.yml")) {
            catalog = Achievements.parse(YamlFile.section(YamlFile.load(config), "achievements"));
        }
        var pages = new AchievementPages(catalog);
        var shown = visible(pages);

        assertEquals(catalog, shown);
        assertTrue(shown.stream().anyMatch(entry -> entry.key().equals("quicksg.pazifist")));
        for (int page = 0; page < pages.count(); page++) {
            assertTrue(pages.page(page).size() <= AchievementPages.ROWS_PER_PAGE);
            pages.page(page).forEach(row -> {
                assertTrue(row.entries().size() <= AchievementPages.ENTRIES_PER_ROW);
                assertTrue(row.entries().stream().allMatch(entry -> entry.game().equals(row.game())));
            });
        }
    }

    @Test
    public void aGameContinuesOnTheNextRowAfterItsEighthEntry() {
        var catalog = entries("quicksg", 9);
        var pages = new AchievementPages(catalog);

        assertEquals(1, pages.count());
        assertEquals(3, pages.inventoryRows());
        assertEquals(List.of(8, 1), pages.page(0).stream().map(row -> row.entries().size()).toList());
        assertEquals(catalog, visible(pages));
    }

    @Test
    public void aSixthGameMovesToTheNextPage() {
        var catalog = new ArrayList<Achievement>();
        for (int game = 0; game < 6; game++) {
            catalog.addAll(entries("game" + game, 8));
        }
        var pages = new AchievementPages(catalog);

        assertEquals(2, pages.count());
        assertEquals(6, pages.inventoryRows());
        assertEquals(5, pages.page(0).size());
        assertEquals("game5", pages.page(1).getFirst().game());
        assertEquals(catalog, visible(pages));
    }

    @Test
    public void anEmptyCatalogStillHasAFooter() {
        var pages = new AchievementPages(List.of());

        assertEquals(1, pages.count());
        assertEquals(1, pages.inventoryRows());
        assertEquals(List.of(), pages.page(0));
    }

    private static List<Achievement> entries(String game, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Achievement(game, "entry" + index, Metric.WINS, 1, 25)).toList();
    }

    private static List<Achievement> visible(AchievementPages pages) {
        return IntStream.range(0, pages.count()).boxed()
                .flatMap(page -> pages.page(page).stream())
                .flatMap(row -> row.entries().stream()).toList();
    }
}
