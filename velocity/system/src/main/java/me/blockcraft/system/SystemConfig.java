package me.blockcraft.system;

import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.system.moderation.PunishmentPreset;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public record JoinMe(int dailyLimit, int resetHour, Map<String, String> gamesByServerPrefix) {
    }

    public record Discovery(int pollSeconds, int staleSeconds) {
    }

    private final Path file;
    private final Map<String, Object> root;
    private final DatabaseConfig database;
    private final Network network;
    private final String lobbyServer;
    private final Discovery discovery;
    private final int maxWarns;
    private final int maxStrikes;
    private final int autoBroadcastMinutes;
    private final JoinMe joinMe;
    private final Set<String> unbannable;
    private final Set<String> blockedCommands;
    private final Map<Integer, PunishmentPreset> punishments;
    private volatile int maxPlayers;
    private volatile String defaultMotd;
    private final List<String> randomMotds;

    private SystemConfig(Path file, Map<String, Object> root) {
        this.file = file;
        this.root = root;
        this.database = DatabaseConfig.fromYaml(YamlFile.section(root, "database"));
        var network = YamlFile.section(root, "network");
        this.network = new Network(string(network, "ip"), string(network, "website"), string(network, "shop"),
                string(network, "teamspeak"), string(network, "discord"));
        this.lobbyServer = string(root, "lobby-server");
        var discovery = YamlFile.section(root, "discovery");
        this.discovery = new Discovery(number(discovery, "poll-seconds", 5), number(discovery, "stale-seconds", 20));
        this.maxWarns = number(root, "max-warns", 8);
        this.maxStrikes = number(root, "max-strikes", 5);
        this.autoBroadcastMinutes = number(root, "auto-broadcast-minutes", 5);
        var joinMe = YamlFile.section(root, "joinme");
        this.joinMe = new JoinMe(number(joinMe, "daily-limit", 6), number(joinMe, "reset-hour", 0),
                YamlFile.section(root, "joinme-games").entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue()))));
        this.unbannable = strings(root, "unbannable").stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        this.blockedCommands = strings(root, "blocked-commands").stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        this.punishments = presets(YamlFile.section(root, "punishments"));
        this.maxPlayers = number(root, "max-players", 100);
        var motd = YamlFile.section(root, "motd");
        this.defaultMotd = string(motd, "default");
        this.randomMotds = new ArrayList<>(strings(motd, "random"));
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

    public String lobbyServer() {
        return lobbyServer;
    }

    public Discovery discovery() {
        return discovery;
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

    public JoinMe joinMe() {
        return joinMe;
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

    private static String string(Map<String, Object> section, String key) {
        return String.valueOf(section.getOrDefault(key, ""));
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    private static List<String> strings(Map<String, Object> section, String key) {
        return section.get(key) instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }
}
