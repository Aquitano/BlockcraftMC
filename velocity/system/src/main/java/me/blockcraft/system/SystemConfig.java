package me.blockcraft.system;

import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.NetworkAddresses;
import me.blockcraft.system.moderation.PunishmentPreset;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** config.yml; the MOTD and player cap are editable in-game and written back to the same file. */
public final class SystemConfig {

    public record Network(String ip, String website, String shop, String teamspeak, String discord) {
    }

    public record JoinMe(int dailyLimit, int resetHour) {
    }

    public record Discovery(int pollSeconds, int staleSeconds, int seatSeconds) {
    }

    public record Friends(int max, int requestMinutes) {
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

    /** {@code file} is relative to the plugin directory and empty disables the pack; {@code port} 0 serves nothing. */
    public record ResourcePack(String file, String url, int port, boolean required) {
    }

    /** {@code groups} are the server groups that see a first join announced and count greetings for it. */
    public record Welcome(boolean enabled, Set<String> groups, int greetingSeconds, int listedGreeters) {
    }

    private final Path file;
    private final Map<String, Object> root;
    private final DatabaseConfig database;
    private final Network network;
    private final TagResolver networkTags;
    private final String lobbyGroup;
    private final Discovery discovery;
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

    private SystemConfig(Path file, Map<String, Object> root) {
        this.file = file;
        this.root = root;
        this.database = DatabaseConfig.fromYaml(YamlFile.section(root, "database"));
        var network = NetworkAddresses.fromYaml(root);
        this.network = new Network(string(network, "ip"), string(network, "website"), string(network, "shop"),
                string(network, "teamspeak"), string(network, "discord"));
        this.networkTags = Messages.tagsOf(network);
        this.lobbyGroup = string(root, "lobby-group");
        var discovery = YamlFile.section(root, "discovery");
        this.discovery = new Discovery(number(discovery, "poll-seconds", 5), number(discovery, "stale-seconds", 20),
                number(discovery, "seat-seconds", 15));
        var alerts = YamlFile.section(root, "alerts");
        this.alerts = new Alerts(env("BLOCKCRAFT_ALERTS_WEBHOOK_URL", string(alerts, "webhook-url")),
                number(alerts, "min-interval-seconds", 300), number(alerts, "lag-mspt", 50), number(alerts, "lag-polls", 6));
        var serverHealth = YamlFile.section(root, "server-health");
        this.serverHealth = new ServerHealth(number(serverHealth, "tps-warn", 19), number(serverHealth, "tps-critical", 15),
                number(serverHealth, "mspt-warn", 40), number(serverHealth, "mspt-critical", 50));
        var resourcePack = YamlFile.section(root, "resource-pack");
        var packUrl = System.getenv("BLOCKCRAFT_RESOURCE_PACK_URL");
        this.resourcePack = new ResourcePack(string(resourcePack, "file"),
                packUrl == null || packUrl.isBlank() ? string(resourcePack, "url") : packUrl,
                number(resourcePack, "port", 0), Boolean.TRUE.equals(resourcePack.get("required")));
        this.maxWarns = number(root, "max-warns", 8);
        this.maxStrikes = number(root, "max-strikes", 5);
        this.autoBroadcastMinutes = number(root, "auto-broadcast-minutes", 5);
        this.historyLimit = Math.max(1, number(YamlFile.section(root, "history"), "limit", 10));
        this.staffLogPageSize = Math.max(1, number(YamlFile.section(root, "stafflog"), "page-size", 10));
        this.watchRecent = Duration.ofDays(Math.max(1, number(YamlFile.section(root, "watch"), "recent-days", 30)));
        var alts = YamlFile.section(root, "alts");
        this.alts = new Alts(Duration.ofDays(Math.max(1, number(alts, "retention-days", 90))),
                Boolean.TRUE.equals(alts.get("block-banned-alts")), number(alts, "purge-hour", 4));
        var freeze = YamlFile.section(root, "freeze");
        this.freeze = new Freeze(number(freeze, "quit-punishment", 0), Duration.ofSeconds(number(freeze, "quit-grace-seconds", 10)));
        this.tabRefreshSeconds = number(YamlFile.section(root, "tab"), "refresh-seconds", 5);
        this.discordCodeMinutes = number(root, "discord-code-minutes", 10);
        var joinMe = YamlFile.section(root, "joinme");
        this.joinMe = new JoinMe(number(joinMe, "daily-limit", 6), number(joinMe, "reset-hour", 0));
        var friends = YamlFile.section(root, "friends");
        this.friends = new Friends(number(friends, "max", 50), number(friends, "request-minutes", 10));
        this.maxIgnored = number(YamlFile.section(root, "ignore"), "max", 50);
        this.infoCommands = infoCommands(root);
        this.unbannable = strings(root, "unbannable").stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        this.blockedCommands = strings(root, "blocked-commands").stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        this.punishments = presets(YamlFile.section(root, "punishments"));
        this.maxPlayers = number(root, "max-players", 100);
        var motd = YamlFile.section(root, "motd");
        this.defaultMotd = string(motd, "default");
        this.randomMotds = new ArrayList<>(strings(motd, "random"));
        var welcome = YamlFile.section(root, "welcome");
        this.welcome = new Welcome(Boolean.TRUE.equals(welcome.get("enabled")), Set.copyOf(strings(welcome, "groups")),
                Math.max(1, number(welcome, "greeting-seconds", 60)), Math.max(1, number(welcome, "listed-greeters", 2)));
        this.serverListFriends = Boolean.TRUE.equals(YamlFile.section(root, "server-list").get("show-friends"));
        this.serverListCache = Duration.ofSeconds(Math.max(1, number(YamlFile.section(root, "server-list"), "cache-seconds", 30)));
        this.calendar = NetworkCalendar.fromYaml(YamlFile.section(root, "calendar"));
    }

    public static SystemConfig load(Path file, Supplier<InputStream> bundledDefault) {
        return new SystemConfig(file, YamlFile.loadWithDefaults(file, bundledDefault));
    }

    public DatabaseConfig database() {
        return database;
    }

    public Network network() {
        return network;
    }

    /** The network addresses as tags, resolved in every message of this plugin. */
    public TagResolver networkTags() {
        return networkTags;
    }

    public String lobbyGroup() {
        return lobbyGroup;
    }

    public Discovery discovery() {
        return discovery;
    }

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

    public NetworkCalendar calendar() {
        return calendar;
    }

    public synchronized void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        save();
    }

    public synchronized void setDefaultMotd(String motd) {
        this.defaultMotd = motd;
        save();
    }

    public synchronized void addRandomMotd(String motd) {
        randomMotds.add(motd);
        save();
    }

    public synchronized String removeRandomMotd(int index) {
        var removed = randomMotds.remove(index);
        save();
        return removed;
    }

    public synchronized int clearRandomMotds() {
        int count = randomMotds.size();
        randomMotds.clear();
        save();
        return count;
    }

    private void save() {
        root.put("max-players", maxPlayers);
        var motd = new LinkedHashMap<String, Object>();
        motd.put("default", defaultMotd);
        motd.put("random", List.copyOf(randomMotds));
        root.put("motd", motd);
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try {
            Files.writeString(file, new Yaml(options).dump(root));
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

    private static String string(Map<String, Object> section, String key) {
        return String.valueOf(section.getOrDefault(key, ""));
    }

    /** Production sets these instead of editing the copied config.yml; blank counts as unset, as for the database. */
    private static String env(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    private static List<String> strings(Map<String, Object> section, String key) {
        return section.get(key) instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }
}
