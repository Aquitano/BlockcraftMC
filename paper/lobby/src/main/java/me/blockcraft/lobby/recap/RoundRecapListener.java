package me.blockcraft.lobby.recap;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.round.RoundRecap;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.config.LobbyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Keeps the last round in the profile; automatic details require the player's preference and an unchanged screen. */
public final class RoundRecapListener implements Listener {

    private static final long AFTER_ARRIVAL_TICKS = 50L;

    private record Recent(RoundRecap recap, Instant received) { }

    private static final class Session {
        private final Player player;
        private final BooleanSupplier currentScreen;
        private Recent recent;
        private boolean settingsLoaded;
        private boolean announced;

        private Session(Player player, BooleanSupplier currentScreen) {
            this.player = player;
            this.currentScreen = currentScreen;
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Plugin plugin;
    private final Messages messages;
    private final LobbyConfig config;
    private final ClickCallback.Options detailsButton;

    public RoundRecapListener(Plugin plugin, Messages messages, LobbyConfig config) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        detailsButton = ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(config.recapMaxAge())
                .build();
        BlockcraftApi.get().profiles().recentRound(this::open);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        var session = new Session(player, BlockcraftApi.get().playerTasks().currentScreen(player));
        sessions.put(id, session);
        Tasks.later(plugin, AFTER_ARRIVAL_TICKS, () -> {
            if (!current(session)) {
                return;
            }
            var api = BlockcraftApi.get();
            api.playerTasks().write(player, () -> api.recaps().take(id, config.recapMaxAge()), recap -> {
                if (!current(session)) {
                    return;
                }
                recap.ifPresent(found -> {
                    session.recent = new Recent(found, Instant.now());
                    announce(session);
                });
            });
        });
    }

    @EventHandler
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.player == event.getPlayer()) {
            session.settingsLoaded = true;
            announce(session);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void announce(Session session) {
        if (!current(session) || !session.settingsLoaded || session.recent == null || session.announced) {
            return;
        }
        session.announced = true;
        var player = session.player;
        if (session.currentScreen.getAsBoolean() && BlockcraftApi.get().settings().enabled(player, PlayerSetting.AUTO_RECAP)) {
            open(player, () -> BlockcraftApi.get().settings().enabled(player, PlayerSetting.AUTO_RECAP));
            return;
        }
        var details = messages.get("recap.details")
                .hoverEvent(HoverEvent.showText(messages.get("recap.details-hover")))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience == player) {
                        Tasks.sync(plugin, () -> {
                            if (current(session)) {
                                Sounds.MENU_CLICK.play(player);
                                open(player);
                            }
                        });
                    }
                }, detailsButton));
        player.sendMessage(messages.get("recap.available", Placeholder.component("details", details)));
    }

    private boolean current(Session session) {
        return plugin.isEnabled() && session.player.isOnline() && sessions.get(session.player.getUniqueId()) == session;
    }

    private void open(Player player) {
        open(player, () -> true);
    }

    private void open(Player player, BooleanSupplier eligible) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.player != player || !current(session)) {
            return;
        }
        var entry = session.recent;
        if (entry == null || entry.received().plus(config.recapMaxAge()).isBefore(Instant.now())) {
            player.sendMessage(messages.get("recap.empty"));
            return;
        }
        var recap = entry.recap();
        new RoundRecapDialog(plugin, messages, player, recap, gameName(recap.game()),
                () -> current(session) && entry.received().plus(config.recapMaxAge()).isAfter(Instant.now())
                        && eligible.getAsBoolean()).open(player);
    }

    /** The navigator's name for the group, since the lobby names games by their card. */
    private Component gameName(String group) {
        return config.navigatorGroups().entrySet().stream()
                .filter(entry -> entry.getValue().equals(group))
                .findFirst()
                .map(entry -> messages.get("navigator." + entry.getKey().key()))
                .orElseGet(() -> Component.text(group));
    }
}
