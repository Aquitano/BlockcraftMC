package me.blockcraft.api.settings;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.task.LatestRequests;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class KitPreferences<K> implements Listener {

    private final Plugin plugin;
    private final PlayerSetting setting;
    private final List<K> kits;
    private final Function<K, String> item;
    private final Predicate<K> free;
    private final BiConsumer<Player, K> restore;
    private final LatestRequests<UUID> requests = new LatestRequests<>();
    private final Set<UUID> remembering = new HashSet<>();

    public KitPreferences(Plugin plugin, PlayerSetting setting, List<K> kits, Function<K, String> item,
                          Predicate<K> free, BiConsumer<Player, K> restore) {
        this.plugin = plugin;
        this.setting = setting;
        this.kits = List.copyOf(kits);
        this.item = item;
        this.free = free;
        this.restore = restore;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void remember(Player player, K kit) {
        var id = player.getUniqueId();
        requests.invalidate(id);
        remembering.add(id);
        try {
            BlockcraftApi.get().settings().set(player, setting, item.apply(kit));
        } finally {
            remembering.remove(id);
        }
    }

    public void restore(Player player) {
        var api = BlockcraftApi.get();
        var stored = api.settings().get(player, setting);
        var kit = kits.stream().filter(candidate -> stored.filter(item.apply(candidate)::equals).isPresent()).findFirst();
        if (kit.isEmpty()) {
            return;
        }
        var selected = kit.get();
        var id = player.getUniqueId();
        var token = requests.begin(id);
        Tasks.async(plugin, () -> free.test(selected) || api.unlocks().owns(id, item.apply(selected)), owned -> {
            if (requests.finish(id, token) && player.isOnline() && owned) {
                restore.accept(player, selected);
            }
        });
    }

    @EventHandler
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler
    public void onSettingChanged(PlayerSettingChangedEvent event) {
        if (event.setting() == setting && !remembering.contains(event.getPlayer().getUniqueId())) {
            restore(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.invalidate(event.getPlayer().getUniqueId());
    }
}
