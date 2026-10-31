package me.blockcraft.lobby.konami;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import me.blockcraft.lobby.konami.KonamiCode.Step;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Input;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.blockcraft.lobby.konami.KonamiCode.SEQUENCE;

/**
 * The Konami code on the movement keys. Only the press of a key counts, and nothing shows before the third right
 * one, so walking around never lights the bar. The full code plays the power-up, finds {@link Secret#RETRO} the
 * first time and hands out the 1UP chat tag.
 */
public final class KonamiListener implements Listener {

    private static final int SHOWN_FROM = 3;
    private static final int BURST_NOTES = 16;
    private static final double BURST_RADIUS = 1.2;
    private static final double BURST_HEIGHT = 1.0;
    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(750));

    private record Reward(boolean secretFound, boolean tagUnlocked) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Unlocks unlocks;
    private final Secrets secrets;
    private final Duration gap;
    private final Map<UUID, Input> inputs = new HashMap<>();
    private final Map<UUID, KonamiCode> codes = new HashMap<>();

    public KonamiListener(Plugin plugin, Messages messages, Unlocks unlocks, Secrets secrets, Duration gap) {
        this.plugin = plugin;
        this.messages = messages;
        this.unlocks = unlocks;
        this.secrets = secrets;
        this.gap = gap;
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        Input before = inputs.put(player.getUniqueId(), event.getInput());
        for (var key : KonamiKey.pressed(before, event.getInput())) {
            var code = codes.computeIfAbsent(player.getUniqueId(), ignored -> new KonamiCode(gap));
            switch (code.press(key, System.nanoTime())) {
                case Step.Advanced(int matched) when matched >= SHOWN_FROM -> advance(player, matched);
                case Step.Missed(int had) when had > SEQUENCE.size() / 2 -> miss(player, had);
                case Step.Completed() -> complete(player);
                default -> {
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inputs.remove(event.getPlayer().getUniqueId());
        codes.remove(event.getPlayer().getUniqueId());
    }

    private void advance(Player player, int matched) {
        BlockcraftApi.get().notifications().feedback(player, bar(matched, "konami.lit"));
        Chiptune.step(player, matched);
    }

    private void miss(Player player, int had) {
        BlockcraftApi.get().notifications().feedback(player, bar(had, "konami.missed"));
        Chiptune.miss(player);
    }

    private void complete(Player player) {
        var notifications = BlockcraftApi.get().notifications();
        notifications.feedback(player, bar(SEQUENCE.size(), "konami.complete"));
        notifications.important(player, Title.title(messages.get("konami.title"), messages.get("konami.subtitle"), TITLE_TIMES));
        Chiptune.powerUp(plugin, player);
        burst(player);
        reward(player);
    }

    /** The ten keys of the code, the first {@code done} of them in {@code doneStyle} and the rest dark. */
    private Component bar(int done, String doneStyle) {
        var keys = new ArrayList<Component>();
        for (int i = 0; i < SEQUENCE.size(); i++) {
            var key = Placeholder.component("key", messages.get(SEQUENCE.get(i).messageKey()));
            keys.add(messages.get(i < done ? doneStyle : "konami.todo", key));
        }
        return messages.get("konami.bar", Placeholder.component("keys", Component.join(JoinConfiguration.spaces(), keys)));
    }

    /** A ring of notes in every colour, for the player and whoever sees them, unless they turned effects off. */
    private static void burst(Player player) {
        var settings = BlockcraftApi.get().settings();
        var center = player.getLocation().add(0, BURST_HEIGHT, 0);
        var viewers = new ArrayList<>(player.getTrackedBy());
        viewers.add(player);
        for (var viewer : viewers) {
            if (!settings.enabled(viewer, PlayerSetting.EFFECTS)) {
                continue;
            }
            for (int i = 0; i < BURST_NOTES; i++) {
                double angle = 2 * Math.PI * i / BURST_NOTES;
                var spot = center.clone().add(Math.cos(angle) * BURST_RADIUS, 0, Math.sin(angle) * BURST_RADIUS);
                viewer.spawnParticle(Particle.NOTE, spot, 0, (double) i / BURST_NOTES, 0, 0, 1);
            }
        }
    }

    /** The first run finds the secret; a later one says the player knows the code instead of "already found". */
    private void reward(Player player) {
        var uuid = player.getUniqueId();
        var tag = ChatTagChoice.of(ChatTag.RETRO);
        Tasks.async(plugin, () -> new Reward(unlocks.owns(uuid, Secret.RETRO.item()), unlocks.unlock(uuid, tag.item())), reward -> {
            if (!player.isOnline()) {
                return;
            }
            if (reward.tagUnlocked()) {
                player.sendMessage(messages.get("konami.tag", Placeholder.component("tag", tag.text(BlockcraftApi.get().messages()))));
            }
            if (reward.secretFound()) {
                player.sendMessage(messages.get("konami.known"));
            } else {
                secrets.find(player, Secret.RETRO);
            }
        });
    }
}
