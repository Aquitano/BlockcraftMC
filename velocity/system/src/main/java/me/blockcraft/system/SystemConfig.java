package me.blockcraft.system;

import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.config.StrictConfig;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.elo.EloFormula;
import me.blockcraft.common.network.NetworkConfig;
import me.blockcraft.system.moderation.PunishmentPreset;
import me.blockcraft.system.network.OnDemand;
import me.blockcraft.system.ranked.RankedQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** config.yml; the MOTD and player cap are editable in-game and written back to the same file. */
public final class SystemConfig {

    public record Network(String ip, String website, String shop, String teamspeak, String discord) {

        public List<String> addresses() {
            return List.of(ip, website, shop, teamspeak, discord);
        }
    }

    public record JoinMe(int dailyLimit, int resetHour) {
    }

    public record Discovery(int pollSeconds, int staleSeconds, int seatSeconds, int maxSnapshotAgeSeconds) {
    }

    /** Game servers started and stopped through the waker at {@code url} with {@code token} (README, "Server discovery and groups"). */
    public record OnDemandConfig(boolean enabled, URI url, String token, OnDemand.Settings settings) {
    }

    public record Friends(int max, int requestMinutes) {
    }

    /** The ranked Duell queue and network.yml's Elo, which the Duell servers rate the series with. */
    public record Ranked(RankedQueue.Settings queue, EloFormula elo) {
    }

    public record Alerts(String webhookUrl, int minIntervalSeconds, int lagMspt, int lagPolls) {
    }

    public record ServerHealth(int tpsWarn, int tpsCritical, int msptWarn, int msptCritical) {
    }

    /** {@code retention} bounds both what {@code /alts} reports and how long an address hash is kept. */
    public record Alts(Duration retention, boolean blockBannedAlts, int purgeHour) {
    }

    /** {@code quitPunishment} is a {@code /strafe} catalogue number, 0 for none. */
    public record Freeze(int quitPunishment, Duration quitGrace) {
    }

    /** A command that prints the {@code info.<name>} lines of messages.yml. */
    public record InfoCommand(String name, List<String> aliases) {
    }

    /**
     * Clients load the base archive and the addons {@code groups} lists for their server group, each
     * {@code blockcraft-<name>.zip} in the plugin directory and at {@code baseUrl}; {@code port} 0 serves nothing. A
     * group listed with no addons (replay) takes those of the recorded game's group. A joining player waits at most
     * {@code waitSeconds} for their client to load them.
     */
    public record ResourcePack(URI baseUrl, int port, int waitSeconds, int downloadRetries,
                               Map<String, List<String>> groups) {
    }

    /** {@code groups} are the server groups that see a first join announced and count greetings for it. */
    public record Welcome(boolean enabled, Set<String> groups, int greetingSeconds, int listedGreeters) {
    }

    private final Path file;
    private final DatabaseConfig database;
    private final Network network;
    private final TagResolver networkTags;
    private final String lobbyGroup;
    private final Discovery discovery;
    private final OnDemandConfig onDemand;
    private final Ranked ranked;
    private final me.blockcraft.system.duel.DuelChallenges.Settings duels;
    private final Alerts alerts;
    private final ServerHealth serverHealth;
    private final ResourcePack resourcePack;
    private final int maxWarns;
    private final int maxStrikes;
    private final int autoBroadcastMinutes;
    private final int historyLimit;
    private final int staffLogPageSize;
    private final Duration watchRecent;
    private final Alts alts;
    private final Freeze freeze;
    private final int tabRefreshSeconds;
    private final int discordCodeMinutes;
    private final JoinMe joinMe;
    private final Friends friends;
    private final int maxIgnored;
    private final List<InfoCommand> infoCommands;
    private final Set<String> unbannable;
    private final Set<String> blockedCommands;
    private final Map<Integer, PunishmentPreset> punishments;
    private volatile int maxPlayers;
    private volatile String defaultMotd;
    private final List<String> randomMotds;
    private final Welcome welcome;
    private final boolean serverListFriends;
    private final Duration serverListCache;
    private final NetworkCalendar calendar;
    private final int calendarPollSeconds;
    private final Duration privateChatCooldown;
    private final Duration reportCooldown;

    private SystemConfig(Path file, Map<String, Object> yaml, NetworkConfig shared, Map<String, String> environment) {
        var root = StrictConfig.of("BlockcraftSystem config.yml", yaml);
        this.file = file;
        this.database = DatabaseConfig.fromYaml(root.section("database"));
        this.network = new Network(address(shared, "ip"), address(shared, "website"), address(shared, "shop"),
                address(shared, "teamspeak"), address(shared, "discord"));
        this.networkTags = shared.tags();
        this.lobbyGroup = root.text("lobby-group");
        var discovery = root.section("discovery");
        this.discovery = new Discovery(discovery.integer("poll-seconds"), discovery.integer("stale-seconds"),
                discovery.integer("seat-seconds"), Math.clamp(discovery.integer("max-snapshot-age-seconds"), 1, 3600));
        var onDemand = root.section("on-demand");
        var onDemandGroups = Arrays.stream(env(environment, "BLOCKCRAFT_ON_DEMAND_GROUPS", "").split(","))
                .map(String::strip).map(group -> group.toLowerCase(Locale.ROOT))
                .filter(group -> !group.isEmpty() && !group.equals(lobbyGroup))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.onDemand = new OnDemandConfig(onDemand.flag("enabled"), URI.create(onDemand.text("url")),
                env(environment, "BLOCKCRAFT_WAKER_TOKEN", onDemand.text("token")),
                new OnDemand.Settings(Collections.unmodifiableSet(onDemandGroups),
                        Duration.ofMinutes(Math.max(1, onDemand.integer("idle-stop-minutes"))),
                        Duration.ofSeconds(Math.max(30, onDemand.integer("start-timeout-seconds"))),
                        Math.max(0, onDemand.integer("max-awake"))));
        var ranked = root.section("ranked");
        var duels = root.section("duels");
        this.duels = new me.blockcraft.system.duel.DuelChallenges.Settings(
                Math.clamp(duels.integer("setup-seconds"), 15, 300),
                Math.clamp(duels.integer("invite-seconds"), 5, 120),
                Math.clamp(duels.integer("transfer-seconds"), 5, 60),
                Math.clamp(duels.integer("cooldown-seconds"), 1, 60));
        this.ranked = new Ranked(new RankedQueue.Settings(
                ranked.text("group").toLowerCase(Locale.ROOT),
                Math.max(0, ranked.integer("base-range")), Math.max(0, ranked.integer("range-per-second")),
                Math.max(0, ranked.integer("max-range")),
                Duration.ofSeconds(Math.max(30, ranked.integer("match-timeout-seconds")))),
                shared.elo());
        var alerts = root.section("alerts");
        this.alerts = new Alerts(env(environment, "BLOCKCRAFT_ALERTS_WEBHOOK_URL", alerts.text("webhook-url")),
                alerts.integer("min-interval-seconds"), alerts.integer("lag-mspt"), alerts.integer("lag-polls"));
        var serverHealth = root.section("server-health");
        this.serverHealth = new ServerHealth(serverHealth.integer("tps-warn"), serverHealth.integer("tps-critical"),
                serverHealth.integer("mspt-warn"), serverHealth.integer("mspt-critical"));
        var resourcePack = root.section("resource-pack");
        var packBaseUrl = env(environment, "BLOCKCRAFT_RESOURCE_PACK_BASE_URL", resourcePack.text("base-url"));
        var packGroups = resourcePack.section("groups");
        this.resourcePack = new ResourcePack(URI.create(packBaseUrl.endsWith("/") ? packBaseUrl : packBaseUrl + "/"),
                resourcePack.integer("port"), Math.max(1, resourcePack.integer("wait-seconds")),
                Math.clamp(resourcePack.integer("download-retries"), 0, 3),
                packGroups.keys().stream().collect(Collectors.toUnmodifiableMap(group -> group, packGroups::texts)));
        this.maxWarns = root.integer("max-warns");
        this.maxStrikes = root.integer("max-strikes");
        this.autoBroadcastMinutes = root.integer("auto-broadcast-minutes");
        this.historyLimit = Math.max(1, root.integer("history.limit"));
        this.staffLogPageSize = Math.max(1, root.integer("stafflog.page-size"));
        this.watchRecent = Duration.ofDays(Math.max(1, root.integer("watch.recent-days")));
        var alts = root.section("alts");
        this.alts = new Alts(Duration.ofDays(Math.max(1, alts.integer("retention-days"))), alts.flag("block-banned-alts"),
                alts.integer("purge-hour"));
        var freeze = root.section("freeze");
        this.freeze = new Freeze(freeze.integer("quit-punishment"), Duration.ofSeconds(freeze.integer("quit-grace-seconds")));
        this.tabRefreshSeconds = root.integer("tab.refresh-seconds");
        this.discordCodeMinutes = root.integer("discord-code-minutes");
        var joinMe = root.section("joinme");
        this.joinMe = new JoinMe(joinMe.integer("daily-limit"), joinMe.integer("reset-hour"));
        var friends = root.section("friends");
        this.friends = new Friends(friends.integer("max"), friends.integer("request-minutes"));
        this.maxIgnored = root.integer("ignore.max");
        this.infoCommands = infoCommands(yaml);
        this.unbannable = root.texts("unbannable").stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        this.blockedCommands = root.texts("blocked-commands").stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        this.punishments = presets(YamlFile.section(yaml, "punishments"));
        this.maxPlayers = root.integer("max-players");
        var motd = root.section("motd");
        this.defaultMotd = motd.text("default");
        this.randomMotds = new ArrayList<>(motd.texts("random"));
        var welcome = root.section("welcome");
        this.welcome = new Welcome(welcome.flag("enabled"), Set.copyOf(welcome.texts("groups")),
                Math.max(1, welcome.integer("greeting-seconds")), Math.max(1, welcome.integer("listed-greeters")));
        var serverList = root.section("server-list");
        this.serverListFriends = serverList.flag("show-friends");
        this.serverListCache = Duration.ofSeconds(Math.max(1, serverList.integer("cache-seconds")));
        this.calendar = shared.calendar();
        this.calendarPollSeconds = shared.calendarPollSeconds();
        this.privateChatCooldown = Duration.ofSeconds(Math.max(0, root.integer("private-chat.cooldown-seconds")));
        this.reportCooldown = Duration.ofSeconds(Math.max(0, root.integer("report.cooldown-seconds")));
    }

    public static SystemConfig load(Path file, Supplier<InputStream> bundledDefault) {
        return load(file, bundledDefault, System.getenv());
    }

    public static SystemConfig load(Path file, Supplier<InputStream> bundledDefault, Map<String, String> environment) {
        return new SystemConfig(file, YamlFile.loadWithDefaults(file, bundledDefault),
                NetworkConfig.load(file.getParent(), environment), environment);
    }

    public DatabaseConfig database() {
        return database;
    }

    public Network network() {
        return network;
    }

    /** network.yml's addresses and palette as tags, resolved in every message of this plugin. */
    public TagResolver networkTags() {
        return networkTags;
    }

    public String lobbyGroup() {
        return lobbyGroup;
    }

    public Discovery discovery() {
        return discovery;
    }

    public OnDemandConfig onDemand() {
        return onDemand;
    }

    public Ranked ranked() {
        return ranked;
    }

    public me.blockcraft.system.duel.DuelChallenges.Settings duels() { return duels; }

    public Alerts alerts() {
        return alerts;
    }

    public ServerHealth serverHealth() {
        return serverHealth;
    }

    public ResourcePack resourcePack() {
        return resourcePack;
    }

    public int maxWarns() {
        return maxWarns;
    }

    public int maxStrikes() {
        return maxStrikes;
    }

    public int autoBroadcastMinutes() {
        return autoBroadcastMinutes;
    }

    public int historyLimit() {
        return historyLimit;
    }

    public int staffLogPageSize() {
        return staffLogPageSize;
    }

    /** How far back a punishment still flags a login for the team. */
    public Duration watchRecent() {
        return watchRecent;
    }

    public Alts alts() {
        return alts;
    }

    public Freeze freeze() {
        return freeze;
    }

    public int tabRefreshSeconds() {
        return tabRefreshSeconds;
    }

    public int discordCodeMinutes() {
        return discordCodeMinutes;
    }

    public JoinMe joinMe() {
        return joinMe;
    }

    public Friends friends() {
        return friends;
    }

    public int maxIgnored() {
        return maxIgnored;
    }

    public List<InfoCommand> infoCommands() {
        return infoCommands;
    }

    public boolean isUnbannable(String name) {
        return unbannable.contains(name.toLowerCase());
    }

    public boolean isBlockedCommand(String command) {
        return blockedCommands.contains(command.toLowerCase());
    }

    public Map<Integer, PunishmentPreset> punishments() {
        return punishments;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public String defaultMotd() {
        return defaultMotd;
    }

    public synchronized List<String> randomMotds() {
        return List.copyOf(randomMotds);
    }

    public Welcome welcome() {
        return welcome;
    }

    public boolean serverListFriends() {
        return serverListFriends;
    }

    /** How long the server list remembers who pinged from an address. */
    public Duration serverListCache() {
        return serverListCache;
    }

    public Duration privateChatCooldown() {
        return privateChatCooldown;
    }

    /** How long a player waits after a report before filing the next one. */
    public Duration reportCooldown() {
        return reportCooldown;
    }

    public NetworkCalendar calendar() {
        return calendar;
    }

    public int calendarPollSeconds() {
        return calendarPollSeconds;
    }

    public synchronized void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        save(editor -> editor.set("max-players", maxPlayers));
    }

    public synchronized void setDefaultMotd(String motd) {
        this.defaultMotd = motd;
        save(editor -> editor.set("motd.default", motd));
    }

    public synchronized void addRandomMotd(String motd) {
        randomMotds.add(motd);
        save(editor -> editor.set("motd.random", List.copyOf(randomMotds)));
    }

    public synchronized String removeRandomMotd(int index) {
        var removed = randomMotds.remove(index);
        save(editor -> editor.set("motd.random", List.copyOf(randomMotds)));
        return removed;
    }

    public synchronized int clearRandomMotds() {
        int count = randomMotds.size();
        randomMotds.clear();
        save(editor -> editor.set("motd.random", List.copyOf(randomMotds)));
        return count;
    }

    private void save(UnaryOperator<ConfigEditor> change) {
        try {
            change.apply(ConfigEditor.open(file)).save();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }

    /** YAML turns the numbered keys into Integers, so the map is read untyped. */
    @SuppressWarnings("unchecked")
    private static Map<Integer, PunishmentPreset> presets(Map<?, ?> section) {
        var presets = new LinkedHashMap<Integer, PunishmentPreset>();
        section.forEach((number, value) -> {
            if (value instanceof Map<?, ?> preset) {
                presets.put(Integer.parseInt(String.valueOf(number)), PunishmentPreset.fromYaml((Map<String, Object>) preset));
            }
        });
        return Map.copyOf(presets);
    }

    private static List<InfoCommand> infoCommands(Map<String, Object> root) {
        if (!(root.get("info-commands") instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<?, ?>) entry)
                .map(entry -> new InfoCommand(String.valueOf(entry.get("name")), entry.get("aliases") instanceof List<?> aliases
                        ? aliases.stream().map(String::valueOf).toList() : List.of()))
                .toList();
    }

    private static String address(NetworkConfig shared, String key) {
        return shared.addresses().getOrDefault(key, "");
    }

    /** Production sets these instead of editing the copied config.yml; blank counts as unset, as for the database. */
    private static String env(Map<String, String> environment, String variable, String fallback) {
        String value = environment.get(variable);
        return value == null || value.isBlank() ? fallback : value;
    }
}
