package me.blockcraft.lobby.daily;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Figures;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.SpotEntities;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Where players take their daily reward: {@code /daily}, the chat line on arrival while today's is still open, and the
 * courier figure at the map's optional {@code daily} spot. Each opens a {@link DailyRewardDialog}.
 */
public final class DailyCourier implements Listener, AutoCloseable {

    public static final String SPOT = "daily";

    private static final long AFTER_ARRIVAL_TICKS = 50L;

    private final Plugin plugin;
    private final Locations<String> spots;
    private final DailyRewards rewards;
    private final DailyRewardTable table;
    private final Messages messages;
    private final SpotEntities.Group<String> courier;
    private final Component claimButton;

    public DailyCourier(Plugin plugin, SpotEntities entities, Locations<String> spots, DailyRewards rewards, Messages messages) {
        this.plugin = plugin;
        this.spots = spots;
        this.rewards = rewards;
        this.messages = messages;
        courier = entities.group((player, spot) -> talk(player));
        table = new DailyRewardTable(BlockcraftApi.get().database(), rewards);
        claimButton = messages.get("daily.claim-button")
                .hoverEvent(HoverEvent.showText(messages.get("daily.claim-hover")))
                .clickEvent(ClickEvent.runCommand("/daily"));
        refresh();
    }

    public void refresh() {
        close();
        spots.get(SPOT).ifPresent(at -> {
            var skin = rewards.skin().isBlank() ? Mannequin.defaultProfile()
                    : ResolvableProfile.resolvableProfile().name(rewards.skin()).build();
            courier.add(SPOT, at, Figures.spawn(at, skin, messages.get("daily.name")));
        });
    }

    /** Told after the arrival has settled, as the round recap is, so the line is not lost behind the loading screen. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        var calendar = BlockcraftApi.get().calendar();
        Tasks.later(plugin, AFTER_ARRIVAL_TICKS, () -> {
            if (!player.isOnline()) {
                return;
            }
            Tasks.async(plugin, () -> table.today(id, calendar.today()), today -> {
                if (!today.claimed() && player.isOnline()) {
                    player.sendMessage(messages.get("daily.available", Formatter.number("coins", today.coins()),
                            Placeholder.component("claim", claimButton)));
                }
            });
        });
    }

    private void talk(Player player) {
        Sounds.SHOP_TALK.play(player);
        open(player);
    }

    public void open(Player player) {
        var id = player.getUniqueId();
        var calendar = BlockcraftApi.get().calendar();
        BlockcraftApi.get().playerTasks().latest(player, () -> table.today(id, calendar.today()), today -> {
            var untilNext = Duration.between(calendar.now(), calendar.today().plusDays(1).atStartOfDay(calendar.zone()));
            new DailyRewardDialog(messages, rewards, today, untilNext, this::claim).open(player);
        });
    }

    private void claim(Player player) {
        var id = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        var calendar = BlockcraftApi.get().calendar();
        BlockcraftApi.get().playerTasks().write(player, () -> table.claim(id, name, calendar.today()),
                paid -> paid.ifPresentOrElse(today -> {
                    BlockcraftApi.get().notifications().rewardSound(player, Sounds.COINS);
                    player.sendMessage(messages.get("daily.claimed", Formatter.number("coins", today.coins()),
                            Formatter.number("streak", today.streak())));
                }, () -> {
                    Sounds.MENU_DENY.play(player);
                    player.sendMessage(messages.get("daily.already-claimed"));
                }));
    }

    @Override
    public void close() {
        courier.close();
    }
}
