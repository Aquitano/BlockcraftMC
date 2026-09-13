package me.blockcraft.core.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.nick.NickRepository;
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
 * Swaps a player's profile for a random name and skin. Paper's setPlayerProfile changes what {@link Player#getName()}
 * returns, so the real name is kept here and the original profile is what {@link #restore} puts back.
 */
public final class Nicks {

    public record Active(String realName, String nick, PlayerProfile original) {
    }

    private final Plugin plugin;
    private final NickRepository repository;
    private final List<String> names;
    private final List<String> skins;
    private final Random random = new Random();
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    public Nicks(Plugin plugin, NickRepository repository, List<String> names, List<String> skins) {
        this.plugin = plugin;
        this.repository = repository;
        this.names = names;
        this.skins = skins;
    }

    public Optional<Active> of(Player player) {
        return Optional.ofNullable(active.get(player.getUniqueId()));
    }

    public String realName(Player player) {
        return of(player).map(Active::realName).orElseGet(player::getName);
    }

    /** Picks and persists a fresh nick; {@code onDone} gets it on the main thread, or empty when the skin could not load. */
    public void assign(Player player, Consumer<Optional<String>> onDone) {
        var nick = new Nick(pickName(), skins.get(random.nextInt(skins.size())));
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> {
            var skin = fetchSkin(nick.skinOwner());
            if (skin.isPresent()) {
                repository.set(uuid, nick);
            }
            return skin;
        }, skin -> {
            if (!player.isOnline()) {
                return;
            }
            skin.ifPresent(textures -> apply(player, nick.name(), textures));
            onDone.accept(skin.map(ignored -> nick.name()));
        });
    }

    /** Re-applies the stored nick when a nicked player arrives on this server. */
    public void applyStored(Player player, Consumer<String> onApplied) {
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.get(uuid).flatMap(nick -> fetchSkin(nick.skinOwner()).map(skin -> Map.entry(nick, skin))),
                stored -> stored.ifPresent(entry -> {
                    if (player.isOnline()) {
                        apply(player, entry.getKey().name(), entry.getValue());
                        onApplied.accept(entry.getKey().name());
                    }
                }));
    }

    /** Returns false when the player wore no nick. */
    public boolean restore(Player player) {
        var current = active.remove(player.getUniqueId());
        if (current == null) {
            return false;
        }
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.clear(uuid));
        player.setPlayerProfile(current.original());
        Bukkit.getPluginManager().callEvent(new NickChangeEvent(player));
        return true;
    }

    public void forget(Player player) {
        active.remove(player.getUniqueId());
    }

    private void apply(Player player, String nick, PlayerProfile skin) {
        var current = active.get(player.getUniqueId());
        var original = current == null ? player.getPlayerProfile() : current.original();
        var profile = Bukkit.createProfileExact(player.getUniqueId(), nick);
        profile.setTextures(skin.getTextures());
        player.setPlayerProfile(profile);
        active.put(player.getUniqueId(), new Active(original.getName(), nick, original));
        Bukkit.getPluginManager().callEvent(new NickChangeEvent(player));
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
