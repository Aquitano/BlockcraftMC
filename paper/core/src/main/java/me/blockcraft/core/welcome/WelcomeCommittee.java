package me.blockcraft.core.welcome;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.player.NewcomerEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.welcome.GreetingRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Willkommenskomitee. While a newcomer's window is open, a chat line that greets counts as greeting them, once
 * per greeter; it pays up to a daily cap, and greeting enough newcomers earns the achievement. Telling the newcomer
 * who greeted them is the proxy's part.
 */
public final class WelcomeCommittee implements Listener {

    private record Window(UUID newcomer, String name, Instant closes) {
    }

    private record Greeting(String newcomer, boolean paid) {
    }

    private record Outcome(List<Greeting> greetings, int greetedInTotal) {
    }

    private final Plugin plugin;
    private final WelcomeConfig config;
    private final GreetingWords words;
    private final GreetingRepository greetings;
    private final Messages messages;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    public WelcomeCommittee(Plugin plugin, WelcomeConfig config, GreetingRepository greetings, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.words = new GreetingWords(config.words());
        this.greetings = greetings;
        this.messages = messages;
    }

    @EventHandler
    public void onNewcomer(NewcomerEvent event) {
        windows.put(event.newcomer(), new Window(event.newcomer(), event.name(), Instant.now().plus(event.window())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var now = Instant.now();
        windows.values().removeIf(window -> !window.closes().isAfter(now));
        var greeter = event.getPlayer();
        var uuid = greeter.getUniqueId();
        var open = windows.values().stream().filter(window -> !window.newcomer().equals(uuid)).toList();
        if (open.isEmpty() || !words.greets(PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            return;
        }
        var shown = greeter.getName();
        var account = BlockcraftApi.get().realName(greeter);
        Tasks.async(plugin, () -> record(uuid, shown, account, open), outcome -> thank(greeter, outcome));
    }

    private Outcome record(UUID greeter, String shown, String account, List<Window> open) {
        var recorded = new ArrayList<Greeting>();
        for (var window : open) {
            var result = greetings.reward(greeter, shown, account, window.newcomer(), config.reward(), config.dailyCap());
            if (result != GreetingRepository.Outcome.ALREADY_GREETED) {
                recorded.add(new Greeting(window.name(), result == GreetingRepository.Outcome.PAID));
            }
        }
        return new Outcome(recorded, recorded.isEmpty() ? 0 : greetings.greetedBy(greeter));
    }

    private void thank(Player greeter, Outcome outcome) {
        if (!greeter.isOnline() || outcome.greetings().isEmpty()) {
            return;
        }
        var api = BlockcraftApi.get();
        var reward = Formatter.number("coins", config.reward());
        outcome.greetings().forEach(greeting -> greeter.sendMessage(messages.get(greeting.paid() ? "welcome.rewarded" : "welcome.unpaid",
                reward, Placeholder.unparsed("newcomer", greeting.newcomer()))));
        if (outcome.greetings().stream().anyMatch(Greeting::paid)) {
            api.notifications().rewardSound(greeter, Sounds.COINS);
        }
        if (outcome.greetedInTotal() >= config.committeeThreshold()) {
            api.achievements().grant(greeter, "network", "willkommenskomitee");
        }
    }
}
