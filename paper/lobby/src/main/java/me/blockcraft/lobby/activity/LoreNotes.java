package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import me.blockcraft.common.shop.UnlockRewards;
import me.blockcraft.lobby.config.LoreNoteConfig;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Notes and books the lobby's builders left lying about, at the map's {@code note-<key>} spots: a click opens one as
 * a book, its pages from {@code lore.<key>.pages}. The first read is an unlock, {@code lobby.lore.<key>}, that pays
 * {@code reward} coins, and the secrets menu lists every note found so far to read again.
 */
public final class LoreNotes implements Listener, AutoCloseable {

    public static final String ITEM_PREFIX = "lobby.lore.";

    private final Plugin plugin;
    private final Locations<String> spots;
    private final LoreNoteConfig config;
    private final Messages messages;
    private final UnlockRewards rewards;
    private final Map<String, LyingItem> lying = new HashMap<>();
    private final ChunkTickets.Owner held;
    private final Set<UUID> busy = new HashSet<>();

    public LoreNotes(Plugin plugin, ChunkTickets chunks, Locations<String> spots, LoreNoteConfig config, Messages messages) {
        this.plugin = plugin;
        held = chunks.owner();
        this.spots = spots;
        this.config = config;
        this.messages = messages;
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
        refresh();
    }

    public static String spot(String note) {
        return "note-" + note;
    }

    public List<String> keys() {
        return List.copyOf(config.notes().keySet());
    }

    public List<String> spots() {
        return keys().stream().map(LoreNotes::spot).toList();
    }

    public static String item(String note) {
        return ITEM_PREFIX + note;
    }

    public void refresh() {
        close();
        config.notes().forEach((key, material) -> spots.get(spot(key)).ifPresent(spot -> {
            var chunk = spot.getChunk();
            held.add(chunk);
            lying.put(key, LyingItem.place(spot, ItemStack.of(material)));
        }));
    }

    /** The note as the secrets menu shows it: its title on the item it lies as once found, a question mark before. */
    public ItemStack icon(String note, boolean found) {
        return found
                ? Items.of(config.notes().getOrDefault(note, Material.PAPER)).name(messages.get("lore." + note + ".title"))
                        .lore(messages.get("lore.menu-found")).build()
                : Items.of(Material.GRAY_DYE).name(messages.get("lore.menu-unknown")).lore(messages.get("lore.menu-unknown-lore")).build();
    }

    public void read(Player player, String note) {
        player.openBook(Book.book(messages.get("lore." + note + ".title"), messages.get("lore." + note + ".author"),
                messages.getLines("lore." + note + ".pages")));
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        byEntity(event.getRightClicked()).ifPresent(note -> {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                find(event.getPlayer(), note);
            }
        });
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        byEntity(event.getAttacked()).ifPresent(note -> {
            event.setCancelled(true);
            find(event.getPlayer(), note);
        });
    }

    private Optional<String> byEntity(Entity entity) {
        return lying.entrySet().stream().filter(entry -> entry.getValue().owns(entity)).map(Map.Entry::getKey).findFirst();
    }

    /** Reads the note; the first time, the unlock and its coins are written in one go before the player hears of it. */
    private void find(Player player, String note) {
        read(player, note);
        var id = player.getUniqueId();
        if (!busy.add(id)) {
            return;
        }
        var name = BlockcraftApi.get().realName(player);
        var unlocks = BlockcraftApi.get().unlocks();
        Tasks.async(plugin, () -> rewards.grant(id, name, item(note), config.reward())
                ? Optional.of(unlocks.owned(id, ITEM_PREFIX).size()) : Optional.<Integer>empty(), found -> {
            busy.remove(id);
            if (!player.isOnline()) {
                return;
            }
            found.ifPresent(count -> {
                Sounds.SECRET.play(player);
                player.sendMessage(messages.get("lore.found", Placeholder.component("note", messages.get("lore." + note + ".title")),
                        Formatter.number("found", count), Formatter.number("total", config.notes().size()),
                        Formatter.number("coins", config.reward())));
            });
        }, () -> busy.remove(id));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        lying.values().forEach(LyingItem::remove);
        lying.clear();
        held.clear();
    }
}
