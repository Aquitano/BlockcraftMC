package me.blockcraft.api.achievement;

import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.cosmetic.CosmeticReward;
import me.blockcraft.api.game.Stats;
import me.blockcraft.api.xp.Levels;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.common.config.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AchievementsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** Granted outside any round, by the lobby parkour and by greeting newcomers, so no recap shows them. */
    private static final Set<String> OUTSIDE_ROUNDS = Set.of("network.ausdauer", "network.parkour-finish", "network.willkommenskomitee");

    private static final List<Achievement> CATALOG = Achievements.parse(Map.of(
            "quicksg", Map.of(
                    "first-win", Map.of("counts", "wins", "threshold", 1, "xp", 25),
                    "kills-10", Map.of("counts", "kills", "threshold", 10, "xp", 25),
                    "kills-100", Map.of("counts", "kills", "threshold", 100, "xp", 100)),
            "duell", Map.of("kills-10", Map.of("counts", "kills", "threshold", 10, "xp", 25)),
            "network", Map.of("level-5", Map.of("counts", "level", "threshold", 5, "xp", 50))));

    @Test
    public void roundTotalsUnlockWhatTheyReachAndNothingOwned() {
        var totals = new Stats(12, 3, 1, 4, 0);

        var reached = Achievements.reached(CATALOG, "quicksg", totals, 1, Set.of("achievement.quicksg.first-win"));

        assertEquals(List.of("quicksg.kills-10"), reached.stream().map(Achievement::key).toList());
    }

    @Test
    public void savedRoundStatsAreEvaluatedImmediatelyEvenAfterThePlayerLeaves() throws Exception {
        var workers = new ArrayList<Runnable>();
        var task = fake(BukkitTask.class, (proxy, method, args) -> null);
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            if (!method.getName().equals("runTaskAsynchronously")) {
                throw new AssertionError("Unexpected scheduler call: " + method.getName());
            }
            workers.add((Runnable) args[1]);
            return task;
        });
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            default -> throw new AssertionError(method.getName());
        });
        var plugin = fake(Plugin.class, (proxy, method, args) -> { throw new AssertionError(method.getName()); });
        var id = UUID.randomUUID();
        var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> false;
            default -> throw new AssertionError(method.getName());
        });
        var xp = new Xp(plugin, null, new Levels(100), Map.of(), null);
        var achievements = new Achievements(plugin, CATALOG, Map.of(), null, xp, null, null, null);
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        var previous = field.get(null);
        try {
            field.set(null, server);
            achievements.afterRound("quicksg", List.of(player));
            assertEquals(1, workers.size());
        } finally {
            field.set(null, previous);
        }
    }

    @Test
    public void levelEntriesFollowTheNetworkLevelAlone() {
        assertEquals(List.of(), Achievements.reached(CATALOG, Achievement.NETWORK, Stats.EMPTY, 4, Set.of()));
        assertEquals(List.of("network.level-5"),
                Achievements.reached(CATALOG, Achievement.NETWORK, Stats.EMPTY, 5, Set.of()).stream().map(Achievement::key).toList());
        assertEquals(Optional.empty(), CATALOG.stream().filter(achievement -> achievement.game().equals(Achievement.NETWORK))
                .findFirst().orElseThrow().tag());
    }

    @Test
    public void levelTagsReconcileEvenWhenAchievementsAreAlreadyOwned() throws Exception {
        try (var config = getClass().getResourceAsStream("/config.yml")) {
            var catalog = Achievements.parse(YamlFile.section(YamlFile.load(config), "achievements"));
            var owned = Set.of("achievement.network.level-5", "achievement.network.level-10",
                    "achievement.network.level-25", "tag.aufsteiger");

            assertEquals(List.of(), Achievements.missingTags(catalog, 4, owned));
            assertEquals(List.of("tag.veteran"), Achievements.missingTags(catalog, 10, owned).stream()
                    .map(tag -> tag.item()).toList());
            var missing = Achievements.missingTags(catalog, 25, owned);
            assertEquals(List.of("tag.veteran", "tag.elite"), missing.stream().map(tag -> tag.item()).toList());
            var reconciled = new HashSet<>(owned);
            missing.forEach(tag -> reconciled.add(tag.item()));
            assertEquals(List.of(), Achievements.missingTags(catalog, 25, reconciled));
            assertEquals(List.of("aufsteiger", "veteran", "elite"),
                    ChatTagChoice.available(false, reconciled).stream().map(ChatTagChoice::value).toList());
        }
    }

    @Test
    public void nextCosmeticAdvancesAtEachConfiguredMilestone() throws Exception {
        try (var config = getClass().getResourceAsStream("/config.yml")) {
            var catalog = Achievements.parse(YamlFile.section(YamlFile.load(config), "achievements"));
            assertEquals(List.of(3, 5, 10, 15, 25, 40),
                    Achievements.levelRewards(catalog).stream().map(Achievement::threshold).toList());
            assertEquals(List.of("effect.win.confetti", "tag.aufsteiger", "tag.veteran", "effect.kill.stardust", "tag.elite", "effect.win.aurora"),
                    Achievements.levelRewards(catalog).stream().map(achievement -> achievement.cosmetic().orElseThrow().item()).toList());
            assertEquals(List.of(25, 50, 100, 100, 250, 300),
                    Achievements.levelRewards(catalog).stream().map(Achievement::xp).toList());
            assertEquals(3, Achievements.nextCosmetic(catalog, 1).orElseThrow().threshold());
            assertEquals(5, Achievements.nextCosmetic(catalog, 3).orElseThrow().threshold());
            assertEquals(10, Achievements.nextCosmetic(catalog, 5).orElseThrow().threshold());
            assertEquals(15, Achievements.nextCosmetic(catalog, 10).orElseThrow().threshold());
            assertEquals(25, Achievements.nextCosmetic(catalog, 15).orElseThrow().threshold());
            assertEquals(40, Achievements.nextCosmetic(catalog, 25).orElseThrow().threshold());
            assertEquals(Optional.empty(), Achievements.nextCosmetic(catalog, 40));
        }
    }

    @Test
    public void cosmeticBackfillUsesOnlyCosmeticOwnershipAtReachedLevels() throws Exception {
        try (var config = getClass().getResourceAsStream("/config.yml")) {
            var catalog = Achievements.parse(YamlFile.section(YamlFile.load(config), "achievements"));
            var owned = catalog.stream().map(Achievement::item).collect(Collectors.toCollection(HashSet::new));
            owned.addAll(Set.of("tag.aufsteiger", "effect.kill.blood", "effect.win.fireworks"));

            assertEquals(List.of(), Achievements.reached(catalog, Achievement.NETWORK, Stats.EMPTY, 40, owned));
            assertEquals(List.of(), Achievements.missingCosmetics(catalog, 2, owned));
            assertEquals(List.of("effect.win.confetti"),
                    Achievements.missingCosmetics(catalog, 3, owned).stream().map(CosmeticReward::item).toList());
            assertEquals(List.of("effect.win.confetti", "tag.veteran", "effect.kill.stardust", "tag.elite", "effect.win.aurora"),
                    Achievements.missingCosmetics(catalog, 40, owned).stream().map(CosmeticReward::item).toList());

            Achievements.missingCosmetics(catalog, 40, owned).forEach(reward -> owned.add(reward.item()));
            assertEquals(List.of(), Achievements.missingCosmetics(catalog, 40, owned));
        }
    }

    @Test
    public void nextCosmeticUsesThresholdsRatherThanCatalogOrderAndSkipsXpOnlyEntries() {
        var catalog = Achievements.parse(Map.of("network", Map.of(
                "later", Map.of("counts", "level", "threshold", 18, "xp", 50, "tag", "elite"),
                "earlier", Map.of("counts", "level", "threshold", 7, "xp", 10, "tag", "aufsteiger"),
                "effect", Map.of("counts", "level", "threshold", 11, "xp", 25, "cosmetic", "effect.win.confetti"),
                "xp-only", Map.of("counts", "level", "threshold", 3, "xp", 5)))).stream()
                .sorted(Comparator.comparingInt(Achievement::threshold).reversed()).toList();

        assertEquals(List.of(7, 11, 18), Achievements.levelRewards(catalog).stream().map(Achievement::threshold).toList());
        assertEquals(7, Achievements.nextCosmetic(catalog, 1).orElseThrow().threshold());
        assertEquals(11, Achievements.nextCosmetic(catalog, 7).orElseThrow().threshold());
        assertEquals(18, Achievements.nextCosmetic(catalog, 11).orElseThrow().threshold());
    }

    @Test
    public void existingConfigInheritsCosmeticsWhileKeepingItsLevelAndXpOverrides() throws Exception {
        var config = folder.newFile("config.yml").toPath();
        Files.writeString(config, """
                achievements:
                  network:
                    level-5: {counts: level, threshold: 8, xp: 75}
                    level-15: {threshold: 17, xp: 125}
                """);
        var merged = YamlFile.loadWithDefaults(config, () -> getClass().getResourceAsStream("/config.yml"));
        var catalog = Achievements.parse(YamlFile.section(merged, "achievements"));
        var next = Achievements.nextCosmetic(catalog, 3).orElseThrow();

        assertEquals(8, next.threshold());
        assertEquals(75, next.xp());
        assertEquals("tag.aufsteiger", next.tag().orElseThrow().item());
        var effect = Achievements.nextCosmetic(catalog, 10).orElseThrow();
        assertEquals(17, effect.threshold());
        assertEquals(125, effect.xp());
        assertEquals("effect.kill.stardust", effect.cosmetic().orElseThrow().item());
        assertEquals(List.of(3, 8, 10, 17, 25, 40),
                Achievements.levelRewards(catalog).stream().map(Achievement::threshold).toList());
        var messages = folder.newFile("messages.yml").toPath();
        Files.writeString(messages, "profile:\n  head-lore: [Eigene Profilzeile]\n");
        var texts = YamlFile.flatten(YamlFile.loadWithDefaults(messages, () -> getClass().getResourceAsStream("/messages.yml")));
        assertEquals("Eigene Profilzeile", texts.get("profile.head-lore"));
        assertEquals("<arrow><primary>Level <level> <dark>· <muted>Dauerhaft", texts.get("profile.level"));
    }

    @Test
    public void tagRewardsMustNameEarnedTagsOnNetworkLevelAchievements() {
        assertThrows(IllegalArgumentException.class, () -> Achievements.parse(Map.of("network", Map.of(
                "premium", Map.of("counts", "level", "threshold", 5, "tag", "pro")))));
        assertThrows(IllegalArgumentException.class, () -> Achievements.parse(Map.of("network", Map.of(
                "moment", Map.of("counts", "moment", "tag", "aufsteiger")))));
        assertThrows(IllegalArgumentException.class, () -> Achievements.parse(Map.of("bedwars", Map.of(
                "kills", Map.of("counts", "kills", "threshold", 5, "tag", "aufsteiger")))));
    }

    @Test
    public void cosmeticRewardsRejectUnknownPurchasedPremiumAndAmbiguousValues() {
        for (var item : List.of("unknown", "effect.win.unknown", "effect.win.fireworks", "effect.kill.blood", "tag.pro")) {
            assertThrows(item, IllegalArgumentException.class, () -> Achievements.parse(Map.of("network", Map.of(
                    "reward", Map.of("counts", "level", "threshold", 3, "cosmetic", item)))));
        }
        assertThrows(IllegalArgumentException.class, () -> Achievements.parse(Map.of("network", Map.of(
                "moment", Map.of("counts", "moment", "cosmetic", "effect.win.confetti")))));
        assertThrows(IllegalArgumentException.class, () -> Achievements.parse(Map.of("bedwars", Map.of(
                "level", Map.of("counts", "level", "threshold", 3, "cosmetic", "effect.win.confetti")))));
        assertThrows(IllegalArgumentException.class, () -> Achievements.parse(Map.of("network", Map.of(
                "multiple", Map.of("counts", "level", "threshold", 3, "tag", "aufsteiger", "cosmetic", "effect.win.confetti")))));
    }

    @Test
    public void everyAchievementIsNamedAndEveryRoundMomentHasARecapLine() throws Exception {
        try (var config = getClass().getResourceAsStream("/config.yml");
             var messages = getClass().getResourceAsStream("/messages.yml")) {
            var catalog = Achievements.parse(YamlFile.section(YamlFile.load(config), "achievements"));
            var texts = YamlFile.flatten(YamlFile.load(messages));
            var missing = new ArrayList<String>();
            for (var achievement : catalog) {
                var required = new ArrayList<>(List.of("achievements." + achievement.key() + ".name",
                        "achievements." + achievement.key() + ".description"));
                if (achievement.isMoment() && !OUTSIDE_ROUNDS.contains(achievement.key())) {
                    required.add("moments." + achievement.name());
                }
                required.stream().filter(key -> !texts.containsKey(key)).forEach(missing::add);
            }
            assertEquals(List.of(), missing);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
