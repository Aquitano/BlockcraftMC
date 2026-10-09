package me.blockcraft.core.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.api.task.SerialTasks;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.nick.NickRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Swaps a player's profile for a random name and skin, and their shown rank for one of the {@code ranks} a nick
 * may wear. Paper's setPlayerProfile changes what {@link Player#getName()} returns, so the real name is kept here
 * and the original profile is what {@link #restore} puts back.
 */
public final class Nicks {

    public record Active(String realName, String nick, PlayerProfile original) {
    }

    private final Plugin plugin;
    private final NickRepository repository;
    private final List<String> names;
    private final List<String> skins;
    private final List<String> ranks;
    private final Random random = new Random();
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final SerialTasks<UUID> writes;

    public Nicks(Plugin plugin, NickRepository repository, List<String> names, List<String> skins, List<String> ranks) {
        this.plugin = plugin;
        this.repository = repository;
        this.names = names;
        this.skins = skins;
        this.ranks = ranks;
        this.writes = new SerialTasks<>(plugin);
    }

    public Optional<Active> of(Player player) {
        return Optional.ofNullable(active.get(player.getUniqueId()));
    }

    /** {@code <rank><nick>}: the worn nick as others read it, with the prefix of the group it wears. */
    public static TagResolver shown(Player player, Nick nick) {
        return TagResolver.resolver(
                Placeholder.component("rank", Ranks.prefix(player).orElse(Component.empty())),
                Placeholder.unparsed("nick", nick.name()));
    }

    /** The LuckPerms groups a nick may show, as config.yml lists them. */
    public List<String> ranks() {
        return ranks;
    }

    /**
     * Picks and persists a fresh nick showing {@code rank}, or one of the {@link #ranks()} at random; {@code onDone}
     * gets the nick on the main thread, or empty when the skin could not load or the nick could not be stored.
     */
    public void assign(Player player, Optional<String> rank, Consumer<Optional<Nick>> onDone) {
        var nick = new Nick(pickName(), skins.get(random.nextInt(skins.size())), rank.orElseGet(() -> ranks.get(random.nextInt(ranks.size()))));
        var uuid = player.getUniqueId();
        writes.submit(uuid, () -> {
            var skin = fetchSkin(nick.skinOwner());
            if (skin.isPresent()) {
                repository.set(uuid, nick);
            }
            return skin;
        }, skin -> {
            if (!player.isOnline()) {
                return;
            }
            skin.ifPresent(textures -> apply(player, nick, textures));
            onDone.accept(skin.map(ignored -> nick));
        }, () -> {
            if (player.isOnline()) {
                onDone.accept(Optional.empty());
            }
        });
    }

    /** Re-applies the stored nick when a nicked player arrives on this server; {@code onDone} hears which one, if any. */
    public void applyStored(Player player, Consumer<Optional<Nick>> onDone) {
        var uuid = player.getUniqueId();
        writes.submit(uuid, () -> repository.get(uuid).flatMap(nick -> fetchSkin(nick.skinOwner()).map(skin -> Map.entry(nick, skin))),
                stored -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    stored.ifPresent(entry -> apply(player, entry.getKey(), entry.getValue()));
                    onDone.accept(stored.map(Map.Entry::getKey));
                }, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(BlockcraftApi.get().messages().get("operation.failed"));
                    }
                });
    }

    /**
     * Throws a stored nick away before it is ever applied, so a player who arrives with one simply appears as
     * themselves; {@code onDropped} runs when there was a nick to drop. The proxy read the nick at login, so it is
     * told even though this server never wore it.
     */
    public void dropStored(Player player, Runnable onDropped) {
        var uuid = player.getUniqueId();
        writes.submit(uuid, () -> repository.clear(uuid), dropped -> {
            if (!dropped || !player.isOnline()) {
                return;
            }
            Bukkit.getPluginManager().callEvent(new NickChangeEvent(player, Optional.empty()));
            ProxyMessaging.nick(plugin, player, Optional.empty());
            onDropped.run();
        }, () -> { });
    }

    /** Returns false when the player wore no nick. */
    public boolean restore(Player player) {
        var current = active.remove(player.getUniqueId());
        if (current == null) {
            return false;
        }
        var uuid = player.getUniqueId();
        writes.run(uuid, () -> repository.clear(uuid));
        player.setPlayerProfile(current.original());
        Bukkit.getPluginManager().callEvent(new NickChangeEvent(player, Optional.empty()));
        ProxyMessaging.nick(plugin, player, Optional.empty());
        return true;
    }

    public void forget(Player player) {
        active.remove(player.getUniqueId());
    }

    private void apply(Player player, Nick nick, PlayerProfile skin) {
        var current = active.get(player.getUniqueId());
        var original = current == null ? player.getPlayerProfile() : current.original();
        var profile = Bukkit.createProfileExact(player.getUniqueId(), nick.name());
        profile.setTextures(skin.getTextures());
        player.setPlayerProfile(profile);
        active.put(player.getUniqueId(), new Active(original.getName(), nick.name(), original));
        Bukkit.getPluginManager().callEvent(new NickChangeEvent(player, Optional.of(nick.rankGroup())));
        ProxyMessaging.nick(plugin, player, Optional.of(nick));
    }

    private static Optional<PlayerProfile> fetchSkin(String owner) {
        var profile = Bukkit.createProfile(owner);
        return profile.complete(true) && profile.hasTextures() ? Optional.of(profile) : Optional.empty();
    }

    private String pickName() {
        var taken = active.values().stream().map(Active::nick).toList();
        for (int attempt = 0; attempt < names.size(); attempt++) {
            var candidate = names.get(random.nextInt(names.size()));
            if (!taken.contains(candidate) && Bukkit.getPlayerExact(candidate) == null) {
                return candidate;
            }
        }
        return names.get(random.nextInt(names.size()));
    }
}
