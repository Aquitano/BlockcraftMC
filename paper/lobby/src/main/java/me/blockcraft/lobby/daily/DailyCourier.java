package me.blockcraft.lobby.daily;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import me.blockcraft.lobby.activity.Figures;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
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
    private final ChunkTickets.Owner held;
    private final Component claimButton;
    private Mannequin figure;

    public DailyCourier(Plugin plugin, ChunkTickets chunks, Locations<String> spots, DailyRewards rewards, Messages messages) {
        this.plugin = plugin;
        this.spots = spots;
        this.rewards = rewards;
        this.messages = messages;
        held = chunks.owner();
        table = new DailyRewardTable(BlockcraftApi.get().database(), rewards);
        claimButton = messages.get("daily.claim-button")
                .hoverEvent(HoverEvent.showText(messages.get("daily.claim-hover")))
                .clickEvent(ClickEvent.runCommand("/daily"));
        refresh();
    }

    public void refresh() {
        close();
        spots.get(SPOT).ifPresent(at -> {
            held.add(at.getChunk());
            var skin = rewards.skin().isBlank() ? Mannequin.defaultProfile()
                    : ResolvableProfile.resolvableProfile().name(rewards.skin()).build();
            figure = Figures.spawn(at, skin, messages.get("daily.name"));
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

    @EventHandler
    public void onTalk(PlayerInteractEntityEvent event) {
        if (isFigure(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                talk(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (isFigure(event.getAttacked())) {
            event.setCancelled(true);
            talk(event.getPlayer());
        }
    }

    private boolean isFigure(Entity entity) {
        return figure != null && figure.equals(entity);
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
        if (figure != null) {
            figure.remove();
            figure = null;
        }
        held.clear();
    }
}
