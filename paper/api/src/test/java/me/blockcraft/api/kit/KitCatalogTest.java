package me.blockcraft.api.kit;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class KitCatalogTest {

    private enum TestKit implements UnlockableKit {
        STANDARD, ARCHER, KNIGHT;

        @Override
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public Material icon() {
            return Material.STONE;
        }

        @Override
        public String itemPrefix() {
            return "test.kit.";
        }
    }

    @Test
    public void ownsTheFreeKitsAndExactlyTheUnlockedOnes() {
        var config = new YamlConfiguration();
        config.set("archer", 0);
        var catalog = KitCatalog.from(config, TestKit.class, kit -> kit == TestKit.STANDARD ? 0 : 300);

        assertEquals(300, catalog.price(TestKit.KNIGHT));
        assertEquals(EnumSet.of(TestKit.STANDARD, TestKit.ARCHER, TestKit.KNIGHT),
                catalog.owned(Set.of("test.kit.knight", "other.kit.archer")));
        assertEquals(EnumSet.of(TestKit.STANDARD, TestKit.ARCHER), catalog.owned(Set.of("other.kit.knight", "test.kit.")));
    }
}
