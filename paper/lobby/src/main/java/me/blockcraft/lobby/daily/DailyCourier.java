package me.blockcraft.lobby.daily;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Figures;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChatTalk;
import me.blockcraft.lobby.SpotEntities;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily reward: the first lobby arrival of a day pays it, and {@code /daily} and the courier figure at the map's
 * optional {@code daily} spot have the courier say the streak in chat, with the coming days, or a button that claims
 * today's reward should the arrival not have paid it.
 */
public final class DailyCourier implements Listener, AutoCloseable {

    public static final String SPOT = "daily";

    private static final long AFTER_ARRIVAL_TICKS = 50L;

    private final Plugin plugin;
    private final Locations<String> spots;
    private final DailyRewards rewards;
    private final DailyRewardTable table;
    private final Messages messages;
    private final ChatTalk talk;
    private final SpotEntities.Group<String> courier;

    public DailyCourier(Plugin plugin, SpotEntities entities, ChatTalk talk, Locations<String> spots, DailyRewards rewards,
                        Messages messages) {
        this.plugin = plugin;
        this.spots = spots;
        this.rewards = rewards;
        this.messages = messages;
        this.talk = talk;
        courier = entities.group((player, spot) -> talk(player));
        table = new DailyRewardTable(BlockcraftApi.get().database(), rewards);
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

    /** Paid after the arrival has settled, as the round recap is told, so the line is not lost behind the loading screen. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        Tasks.later(plugin, AFTER_ARRIVAL_TICKS, () -> {
            if (player.isOnline()) {
                pay(player, () -> {});
            }
        });
    }

    private void talk(Player player) {
        if (talk.ready(player, SPOT)) {
            Sounds.SHOP_TALK.play(player);
            open(player);
        }
    }

    public void open(Player player) {
        var id = player.getUniqueId();
        var calendar = BlockcraftApi.get().calendar();
        BlockcraftApi.get().playerTasks().latest(player, () -> table.today(id, calendar.today()), today -> {
            var untilNext = Duration.between(calendar.now(), calendar.today().plusDays(1).atStartOfDay(calendar.zone()));
            lines(messages, today, untilNext, buttons(player, today)).forEach(player::sendMessage);
        });
    }

    private List<Component> buttons(Player player, DailyRewards.Today today) {
        var reward = TagResolver.resolver(Formatter.number("coins", today.coins()), Formatter.number("streak", today.streak()));
        var buttons = new ArrayList<Component>();
        if (!today.claimed()) {
            buttons.add(talk.once(player, messages.get("daily.take", reward), messages.get("daily.take-hover", reward), this::claim));
        }
        if (rewards.previewDays() > 0) {
            buttons.add(talk.button(player, messages.get("daily.upcoming-button"), messages.get("daily.upcoming-hover"),
                    viewer -> viewer.sendMessage(upcoming(messages, rewards, today))));
        }
        return buttons;
    }

    /** Today's offer, or once it is taken, when the next one comes; then the buttons, if there are any. */
    static List<Component> lines(Messages messages, DailyRewards.Today today, Duration untilNext, List<Component> buttons) {
        var name = messages.get("daily.name");
        var streak = Formatter.number("streak", today.streak());
        var said = today.claimed()
                ? messages.get("daily.done", streak, Formatter.number("hours", untilNext.toHours()),
                        Formatter.number("minutes", untilNext.toMinutesPart()))
                : messages.get("daily.offer", streak, Formatter.number("coins", today.coins()));
        var lines = new ArrayList<Component>();
        lines.add(ChatTalk.says(messages, name, said));
        if (!buttons.isEmpty()) {
            lines.add(messages.get("daily.actions", Placeholder.component("buttons", Component.join(JoinConfiguration.spaces(), buttons))));
        }
        return lines;
    }

    static Component upcoming(Messages messages, DailyRewards rewards, DailyRewards.Today today) {
        var days = new ArrayList<Component>();
        for (int day = today.streak() + 1; day <= today.streak() + rewards.previewDays(); day++) {
            days.add(messages.get("daily.upcoming-day", Formatter.number("day", day), Formatter.number("coins", rewards.coins(day))));
        }
        return ChatTalk.says(messages, messages.get("daily.name"),
                messages.get("daily.upcoming", Placeholder.component("days", Component.join(JoinConfiguration.commas(true), days))));
    }

    private void claim(Player player) {
        pay(player, () -> {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("daily.already-claimed"));
        });
    }

    private void pay(Player player, Runnable alreadyPaid) {
        var id = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        var calendar = BlockcraftApi.get().calendar();
        BlockcraftApi.get().playerTasks().write(player, () -> table.claim(id, name, calendar.today()),
                paid -> paid.ifPresentOrElse(today -> {
                    BlockcraftApi.get().notifications().rewardSound(player, Sounds.COINS);
                    player.sendMessage(messages.get("daily.claimed", Formatter.number("coins", today.coins()),
                            Formatter.number("streak", today.streak())));
                }, alreadyPaid));
    }

    @Override
    public void close() {
        courier.close();
    }
}
