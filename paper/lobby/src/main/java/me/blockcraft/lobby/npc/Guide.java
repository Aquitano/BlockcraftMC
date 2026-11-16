package me.blockcraft.lobby.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.api.dialogue.Dialogue;
import me.blockcraft.api.display.Figures;
import me.blockcraft.api.player.WelcomeEvent;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.SpotEntities;
import me.blockcraft.lobby.config.GuideConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The guide at the map's optional {@code guide} spot by the spawn. A player new to the network hears its tips in the
 * dialogue panel when they arrive, and a chat line about the rules after them; a click on the guide repeats the tips.
 * The panel needs the pack, which can load after the arrival, so a newcomer waits for it a little and hears the tips
 * in chat when it does not come.
 */
public final class Guide implements Listener, AutoCloseable {

    public static final String SPOT = "guide";

    private static final List<String> TIPS = List.of("navigator", "daily", "friends");
    private static final ClickCallback.Options RULES_BUTTON = ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();

    private final Plugin plugin;
    private final Locations<String> spots;
    private final GuideConfig config;
    private final Messages messages;
    private final Dialogue tips;
    private final SpotEntities.Group<String> placed;
    private final Map<UUID, BukkitTask> awaitingPack = new HashMap<>();
    private final Map<UUID, Component> rules = new HashMap<>();

    public Guide(Plugin plugin, SpotEntities entities, Locations<String> spots, GuideConfig config, Messages messages) {
        this.plugin = plugin;
        this.spots = spots;
        this.config = config;
        this.messages = messages;
        tips = tips();
        placed = entities.group((player, spot) -> talk(player));
        refresh();
    }

    private static Dialogue tips() {
        var lines = new LinkedHashMap<String, Dialogue.Line>();
        for (int index = 0; index < TIPS.size(); index++) {
            String next = index + 1 < TIPS.size() ? TIPS.get(index + 1) : "";
            lines.put(TIPS.get(index), new Dialogue.Line("guide.name", "guide.tips." + TIPS.get(index), Dialogue.Portrait.GUIDE, next, List.of()));
        }
        return new Dialogue(TIPS.getFirst(), lines);
    }

    public void refresh() {
        close();
        spots.get(SPOT).ifPresent(at -> {
            var skin = config.skin().isBlank() ? Mannequin.defaultProfile() : ResolvableProfile.resolvableProfile().name(config.skin()).build();
            placed.add(SPOT, at, Figures.spawn(at, skin, messages.get("guide.name")));
        });
    }

    @EventHandler
    public void onWelcome(WelcomeEvent event) {
        var player = event.getPlayer();
        rules.put(player.getUniqueId(), event.rules());
        Tasks.later(plugin, config.greetAfterTicks(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (BlockcraftApi.get().resourcePack().applied(player)) {
                welcome(player);
                return;
            }
            awaitingPack.put(player.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                awaitingPack.remove(player.getUniqueId());
                welcome(player);
            }, config.packWaitTicks()));
        });
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent event) {
        var player = event.getPlayer();
        if (!BlockcraftApi.get().resourcePack().applied(player)) {
            return;
        }
        var waiting = awaitingPack.remove(player.getUniqueId());
        if (waiting != null) {
            waiting.cancel();
            welcome(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var waiting = awaitingPack.remove(event.getPlayer().getUniqueId());
        if (waiting != null) {
            waiting.cancel();
        }
        rules.remove(event.getPlayer().getUniqueId());
    }

    private void welcome(Player player) {
        if (!player.isOnline()) {
            return;
        }
        var scope = new PresentationScope(plugin);
        if (BlockcraftApi.get().resourcePack().applied(player)
                && BlockcraftApi.get().dialogues().start(player, tips, messages, scope, ending -> { })) {
            scope.own(() -> pointToRules(player));
        } else {
            scope.close();
            tipsInChat(player);
            pointToRules(player);
        }
    }

    private void talk(Player player) {
        Sounds.SHOP_TALK.play(player);
        BlockcraftApi.get().dialogues().startOrElse(player, tips, messages, ending -> { }, () -> tipsInChat(player));
    }

    private void tipsInChat(Player player) {
        var name = Placeholder.component("name", messages.get("guide.name"));
        TIPS.forEach(tip -> player.sendMessage(messages.get("guide.chat", name,
                Placeholder.component("text", messages.get("guide.tips." + tip)))));
    }

    private void pointToRules(Player player) {
        var text = rules.remove(player.getUniqueId());
        if (text == null || !player.isOnline()) {
            return;
        }
        var button = messages.get("guide.rules-button")
                .hoverEvent(HoverEvent.showText(messages.get("guide.rules-hover")))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience == player) {
                        Tasks.sync(plugin, () -> showRules(player, text));
                    }
                }, RULES_BUTTON));
        player.sendMessage(messages.get("guide.rules", Placeholder.component("button", button)));
    }

    private void showRules(Player player, Component text) {
        if (!player.isOnline()) {
            return;
        }
        Dialogs.show(player, Dialogs.create(messages.get("guide.rules-title"), List.of(DialogBody.plainMessage(text)), List.of(),
                Dialogs.actions(List.of(), Dialogs.button(BlockcraftApi.get().messages().get("menu.close")))));
    }

    @Override
    public void close() {
        placed.close();
    }
}
