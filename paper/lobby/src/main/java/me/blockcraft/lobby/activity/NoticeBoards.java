package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.menu.ChangelogDialog;
import me.blockcraft.lobby.npc.Holograms;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The two boards at spawn, books lying at the map's {@code board-changelog} and {@code board-rules} spots under a
 * card that names them: the changelog opens as a dialog, the rules come from the proxy's rules command, which holds
 * the one copy of them. A returning player whose last seen changelog is older than {@code version} gets it shown
 * once on joining; a player without any network XP yet is new, sees the welcome instead and counts as up to date.
 */
public final class NoticeBoards implements Listener, AutoCloseable {

    public static final List<String> SPOTS = List.of("board-changelog", "board-rules");

    private static final long SHOW_AFTER_JOIN_TICKS = 100L;
    private static final double CARD_HEIGHT = 1.6;

    private final Plugin plugin;
    private final Locations<String> spots;
    private final Messages messages;
    private final String version;
    private final String rulesCommand;
    private final ChangelogDialog changelog;
    private final Map<String, LyingItem> lying = new HashMap<>();
    private final List<TextDisplay> cards = new ArrayList<>();
    private final List<Chunk> held = new ArrayList<>();

    public NoticeBoards(Plugin plugin, Locations<String> spots, Messages messages, String version, String rulesCommand) {
        this.plugin = plugin;
        this.spots = spots;
        this.messages = messages;
        this.version = version;
        this.rulesCommand = rulesCommand;
        this.changelog = new ChangelogDialog(messages, version);
        refresh();
    }

    public void refresh() {
        close();
        for (var spot : SPOTS) {
            spots.get(spot).ifPresent(at -> {
                var chunk = at.getChunk();
                chunk.addPluginChunkTicket(plugin);
                held.add(chunk);
                lying.put(spot, LyingItem.place(at, ItemStack.of(spot.equals("board-rules") ? Material.WRITABLE_BOOK : Material.BOOK)));
                var card = Holograms.line(at.toBlockLocation().add(0.5, CARD_HEIGHT, 0.5), messages.get("boards." + spot));
                card.setPersistent(false);
                cards.add(card);
            });
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        board(event.getRightClicked()).ifPresent(board -> {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                open(event.getPlayer(), board);
            }
        });
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        board(event.getAttacked()).ifPresent(board -> {
            event.setCancelled(true);
            open(event.getPlayer(), board);
        });
    }

    private Optional<String> board(Entity entity) {
        return lying.entrySet().stream().filter(entry -> entry.getValue().owns(entity)).map(Map.Entry::getKey).findFirst();
    }

    private void open(Player player, String board) {
        Sounds.MENU_OPEN.play(player);
        if (board.equals("board-rules")) {
            ProxyMessaging.runOnProxy(plugin, player, rulesCommand);
        } else {
            changelog.open(player);
            seen(player);
        }
    }

    /** The player's settings load as they join, so the check waits a few seconds, which also lets the lobby settle. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        Tasks.later(plugin, SHOW_AFTER_JOIN_TICKS, () -> {
            var api = BlockcraftApi.get();
            if (!player.isOnline() || api.settings().get(player, PlayerSetting.SEEN_CHANGELOG).filter(version::equals).isPresent()) {
                return;
            }
            if (api.xp().total(player) > 0) {
                changelog.open(player);
            }
            seen(player);
        });
    }

    private void seen(Player player) {
        BlockcraftApi.get().settings().set(player, PlayerSetting.SEEN_CHANGELOG, version);
    }

    @Override
    public void close() {
        lying.values().forEach(LyingItem::remove);
        lying.clear();
        cards.forEach(TextDisplay::remove);
        cards.clear();
        held.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        held.clear();
    }
}
