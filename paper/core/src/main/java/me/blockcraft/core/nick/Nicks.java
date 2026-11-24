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

import java.util.ArrayList;
import java.util.Collections;
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

    private record Worn(Nick nick, Optional<PlayerProfile> skin) {
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
                Placeholder.component("rank",
                        Ranks.prefix(player).map(rank -> rank.shownTo(player)).orElse(Component.empty())),
                Placeholder.unparsed("nick", nick.name()));
    }

    /** The LuckPerms groups a nick may show, as config.yml lists them. */
    public List<String> ranks() {
        return ranks;
    }

    /**
     * Picks and persists a fresh nick showing {@code rank}, or one of the {@link #ranks()} at random, skipping every
     * name someone on the network already goes by; {@code onDone} gets the nick on the main thread, or empty when
     * the skin could not load, no name was free or the nick could not be stored.
     */
    public void assign(Player player, Optional<String> rank, Consumer<Optional<Nick>> onDone) {
        String skinOwner = skins.get(random.nextInt(skins.size()));
        String rankGroup = rank.orElseGet(() -> ranks.get(random.nextInt(ranks.size())));
        var candidates = freeNames();
        var uuid = player.getUniqueId();
        writes.submit(uuid, () -> fetchSkin(skinOwner).flatMap(skin -> candidates.stream()
                .map(name -> new Nick(name, skinOwner, rankGroup))
                .filter(nick -> repository.claim(uuid, nick))
                .findFirst()
                .map(nick -> new Worn(nick, Optional.of(skin)))), worn -> {
            if (!player.isOnline()) {
                return;
            }
            worn.ifPresent(chosen -> apply(player, chosen));
            onDone.accept(worn.map(Worn::nick));
        }, () -> {
            if (player.isOnline()) {
                onDone.accept(Optional.empty());
            }
        });
    }

    /**
     * Re-applies the stored nick when a nicked player arrives on this server; {@code onDone} hears which one, if any.
     * A skin that cannot load leaves the default one rather than unmasking the player.
     */
    public void applyStored(Player player, Consumer<Optional<Nick>> onDone) {
        var uuid = player.getUniqueId();
        writes.submit(uuid, () -> repository.get(uuid).map(nick -> new Worn(nick, fetchSkin(nick.skinOwner()))),
                stored -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    stored.ifPresent(worn -> apply(player, worn));
                    onDone.accept(stored.map(Worn::nick));
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

    private void apply(Player player, Worn worn) {
        var nick = worn.nick();
        var current = active.get(player.getUniqueId());
        var original = current == null ? player.getPlayerProfile() : current.original();
        var profile = Bukkit.createProfileExact(player.getUniqueId(), nick.name());
        worn.skin().ifPresent(skin -> profile.setTextures(skin.getTextures()));
        player.setPlayerProfile(profile);
        active.put(player.getUniqueId(), new Active(original.getName(), nick.name(), original));
        Bukkit.getPluginManager().callEvent(new NickChangeEvent(player, Optional.of(nick.rankGroup())));
        ProxyMessaging.nick(plugin, player, Optional.of(nick));
    }

    private static Optional<PlayerProfile> fetchSkin(String owner) {
        var profile = Bukkit.createProfile(owner);
        return profile.complete(true) && profile.hasTextures() ? Optional.of(profile) : Optional.empty();
    }

    /** The configured names in random order, without those of players on this server; the database checks the rest. */
    private List<String> freeNames() {
        var free = new ArrayList<>(names.stream().filter(name -> Bukkit.getPlayerExact(name) == null).toList());
        Collections.shuffle(free, random);
        return free;
    }
}
