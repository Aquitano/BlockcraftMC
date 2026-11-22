package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Cards;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.ChatTalk;
import me.blockcraft.lobby.SpotEntities;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The two boards at spawn, books lying at the map's {@code board-changelog} and {@code board-rules} spots under a
 * card that names them. Both print in chat: the changelog from {@code changelog} in messages.yml, the rules from the
 * proxy's rules command, which holds the one copy of them. A returning player whose last seen changelog is older than
 * {@code version} gets it printed once on joining; a player without any network XP yet is new, sees the welcome
 * instead and counts as up to date.
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
    private final ChatTalk talk;
    private final SpotEntities.Group<String> boards;

    public NoticeBoards(Plugin plugin, SpotEntities entities, ChatTalk talk, Locations<String> spots, Messages messages, String version,
                        String rulesCommand) {
        this.plugin = plugin;
        boards = entities.group(this::open);
        this.spots = spots;
        this.messages = messages;
        this.version = version;
        this.rulesCommand = rulesCommand;
        this.talk = talk;
        refresh();
    }

    public void refresh() {
        close();
        for (var spot : SPOTS) {
            spots.get(spot).ifPresent(at -> {
                var book = LyingItem.place(at, ItemStack.of(spot.equals("board-rules") ? Material.WRITABLE_BOOK : Material.BOOK));
                var card = Cards.line(at.toBlockLocation().add(0.5, CARD_HEIGHT, 0.5), messages.get("boards." + spot));
                card.setPersistent(false);
                boards.add(spot, at, book.display(), book.box(), card);
            });
        }
    }

    private void open(Player player, String board) {
        if (!talk.ready(player, board)) {
            return;
        }
        Sounds.MENU_OPEN.play(player);
        if (board.equals("board-rules")) {
            ProxyMessaging.runOnProxy(plugin, player, rulesCommand);
        } else {
            showChangelog(player);
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
                showChangelog(player);
            }
            seen(player);
        });
    }

    private void showChangelog(Player player) {
        player.sendMessage(messages.get("changelog.title", Placeholder.unparsed("version", version)));
        messages.getLines("changelog.entries").forEach(player::sendMessage);
    }

    private void seen(Player player) {
        BlockcraftApi.get().settings().set(player, PlayerSetting.SEEN_CHANGELOG, version);
    }

    @Override
    public void close() {
        boards.close();
    }
}
