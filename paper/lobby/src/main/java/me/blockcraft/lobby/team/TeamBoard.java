package me.blockcraft.lobby.team;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

public final class TeamBoard {

    private final Path source;
    private final Path active;
    private final Messages messages;
    private final Function<String, ResolvableProfile> profiles;

    public TeamBoard(Path source, Path active, Messages messages) {
        this(source, active, messages, name -> ResolvableProfile.resolvableProfile().name(name).build());
    }

    TeamBoard(Path source, Path active, Messages messages, Function<String, ResolvableProfile> profiles) {
        this.source = source;
        this.active = active;
        this.messages = messages;
        this.profiles = profiles;
    }

    public record Result(int members, int slots, List<String> problems) {
    }

    private record Slot(Skull head, Sign sign) {
    }

    private record Member(String name, String role) {
    }

    public Result refresh(boolean apply) {
        var problems = new ArrayList<String>();
        if (!Files.isRegularFile(source)) {
            return new Result(0, 0, List.of(source.toString()));
        }
        var yaml = YamlConfiguration.loadConfiguration(source.toFile());
        var slots = new LinkedHashMap<String, Slot>();
        var members = new LinkedHashMap<String, Member>();
        var occupied = new HashSet<Location>();
        var title = sign(yaml, "title", occupied, problems);
        var emptySkin = yaml.getString("empty-skin", "");
        if (!playerName(emptySkin)) {
            problems.add("empty-skin");
        }
        var layout = yaml.getConfigurationSection("slots");
        if (layout == null || layout.getKeys(false).isEmpty()) {
            problems.add("slots");
        } else {
            for (var key : layout.getKeys(false)) {
                var path = "slots." + key;
                var headLocation = location(yaml, path + ".head", occupied, problems);
                var sign = sign(yaml, path + ".sign", occupied, problems);
                Skull head = null;
                if (headLocation != null) {
                    var block = headLocation.getBlock();
                    if ((block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD)
                            && block.getState() instanceof Skull skull) {
                        head = skull;
                    } else {
                        problems.add(path + ".head");
                    }
                }
                if (head != null && sign != null) {
                    slots.put(key, new Slot(head, sign));
                }
            }
        }
        var roster = yaml.getConfigurationSection("members");
        if (roster == null) {
            problems.add("members");
        } else {
            for (var key : roster.getKeys(false)) {
                var path = "members." + key;
                var name = roster.getString(key + ".name", "");
                var role = roster.getString(key + ".role", "");
                if (!slots.containsKey(key) || !playerName(name) || role.isBlank()
                        || role.codePoints().anyMatch(Character::isISOControl)) {
                    problems.add(path);
                } else {
                    members.put(key, new Member(name, role));
                }
            }
        }
        if (!problems.isEmpty() || !apply) {
            return new Result(members.size(), slots.size(), List.copyOf(problems));
        }
        if (!source.toAbsolutePath().normalize().equals(active.toAbsolutePath().normalize())) {
            try {
                Files.copy(source, active, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                return new Result(members.size(), slots.size(), List.of(active.toString()));
            }
        }
        for (var entry : slots.entrySet()) {
            var slot = entry.getValue();
            var member = members.get(entry.getKey());
            slot.head().setProfile(profiles.apply(member == null ? emptySkin : member.name()));
            slot.head().update(false, false);
            lines(slot.sign(), member == null ? messages.getLines("teamboard.sign.empty")
                    : messages.getLines("teamboard.sign.member", Placeholder.unparsed("name", member.name()),
                            Placeholder.unparsed("role", member.role())));
        }
        lines(title, messages.getLines("teamboard.sign.title"));
        return new Result(members.size(), slots.size(), List.of());
    }

    private static boolean playerName(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static Location location(ConfigurationSection yaml, String path, HashSet<Location> occupied,
                                     List<String> problems) {
        var location = yaml.getLocation(path);
        if (location == null || !location.isWorldLoaded() || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
                || location.getY() < location.getWorld().getMinHeight() || location.getY() >= location.getWorld().getMaxHeight()) {
            problems.add(path);
            return null;
        }
        var block = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!occupied.add(block)) {
            problems.add(path);
            return null;
        }
        return block;
    }

    private static Sign sign(ConfigurationSection yaml, String path, HashSet<Location> occupied, List<String> problems) {
        var location = location(yaml, path, occupied, problems);
        if (location == null) {
            return null;
        }
        if (location.getBlock().getState() instanceof Sign sign) {
            return sign;
        }
        problems.add(path);
        return null;
    }

    private static void lines(Sign sign, List<Component> lines) {
        for (int index = 0; index < 4; index++) {
            sign.getSide(Side.FRONT).line(index, index < lines.size() ? lines.get(index) : Component.empty());
        }
        sign.update(false, false);
    }
}
