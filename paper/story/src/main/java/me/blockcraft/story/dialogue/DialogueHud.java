package me.blockcraft.story.dialogue;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A conversation in the panel above the hotbar, independent of whatever runs the conversation. A line types out,
 * paced by its recording when it has one; sneak shows the rest of a page, turns the page, confirms the highlighted
 * answer or closes a line without answers. The number keys pick an answer at once and the scroll wheel moves the
 * highlight: while the panel is open the hotbar rests on a slot whose neighbours lie beyond the numbered answers, so a
 * scroll and a number key never mean the same slot.
 */
public final class DialogueHud implements Listener, AutoCloseable {

    /** One spoken line: who says it, the words, and the recording that plays with them. */
    public record Line(String speaker, String text, Optional<Voices.Voice> voice) { }

    /** How the panel hands the player's input back to whatever runs the conversation. */
    public interface Reply {
        /** The player confirmed the answer at {@code index}, counted from zero. */
        void chosen(int index);

        /** The player read a line that had no answers to its end, or it lingered unanswered for long enough. */
        void read();

        /** The panel closed, whoever closed it. */
        void closed();
    }

    public record Settings(double charactersPerTick, int lingerTicks, int restingSlot, Hints hints) {
        public record Hints(String advance, String choose, String close) { }

        public static Settings from(ConfigurationSection section, Hints hints) {
            return new Settings(section.getDouble("characters-per-tick"), section.getInt("linger-seconds") * 20,
                    section.getInt("resting-slot"), hints);
        }
    }

    private static final int RISE = 6;
    private static final int TYPE_EVERY = 3;
    private static final int REFRESH_TICKS = 20;
    private static final double LEAVE = 6;

    private final Settings settings;
    private final Map<UUID, Showing> showing = new HashMap<>();
    private final BukkitTask task;

    private final class Showing {
        final Player player;
        final int previousSlot;
        final Location openedAt;
        Line line;
        List<List<String>> pages;
        List<String> answers;
        Reply reply;
        int page;
        double revealed;
        double rate;
        int age;
        int lineAge;
        int selected;
        boolean answered;
        boolean sneaking;
        Component drawn;

        Showing(Player player) {
            this.player = player;
            this.previousSlot = player.getInventory().getHeldItemSlot();
            this.openedAt = player.getLocation();
        }

        List<String> current() {
            return pages.get(page);
        }

        boolean pageRevealed() {
            return revealed >= DialoguePanel.length(current());
        }

        boolean choosing() {
            return !answered && !answers.isEmpty() && pageRevealed() && page == pages.size() - 1;
        }
    }

    public DialogueHud(Plugin plugin, Settings settings) {
        this.settings = settings;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public boolean showing(Player player) {
        return showing.containsKey(player.getUniqueId());
    }

    /** Whether the line on {@code player}'s panel is still typing out or its recording still playing. */
    public boolean speaking(Player player) {
        var view = showing.get(player.getUniqueId());
        return view != null && !view.answered
                && (!view.pageRevealed() || view.lineAge < view.line.voice().map(Voices.Voice::ticks).orElse(0));
    }

    /** Shows {@code line} with {@code answers} to choose from once it is read; no answers means it closes when read. */
    public void say(Player player, Line line, List<String> answers, Reply reply) {
        var view = showing.get(player.getUniqueId());
        if (view == null) {
            view = new Showing(player);
            showing.put(player.getUniqueId(), view);
            player.getInventory().setHeldItemSlot(settings.restingSlot());
            StorySound.DIALOGUE_OPEN.play(player);
        } else {
            view.line.voice().ifPresent(voice -> player.stopSound(SoundStop.named(voice.sound())));
        }
        view.line = line;
        view.pages = DialoguePanel.pages(line.text());
        view.answers = answers.stream().limit(DialoguePanel.MAX_ANSWERS).toList();
        view.reply = reply;
        view.page = 0;
        view.revealed = 0;
        view.lineAge = 0;
        view.selected = 0;
        view.answered = false;
        int characters = view.pages.stream().mapToInt(DialoguePanel::length).sum();
        view.rate = line.voice().map(voice -> Math.max(settings.charactersPerTick() / 2, characters / (voice.ticks() * 0.85)))
                .orElse(settings.charactersPerTick());
        line.voice().ifPresent(voice -> player.playSound(Sound.sound(voice.sound(), Sound.Source.VOICE, 1, 1), Sound.Emitter.self()));
        draw(view, true);
    }

    public void close(Player player) {
        var view = showing.remove(player.getUniqueId());
        if (view == null) return;
        view.line.voice().ifPresent(voice -> player.stopSound(SoundStop.named(voice.sound())));
        if (player.isOnline()) {
            player.getInventory().setHeldItemSlot(view.previousSlot);
            player.sendActionBar(Component.empty());
            StorySound.DIALOGUE_CLOSE.play(player);
        }
        view.reply.closed();
    }

    private void tick() {
        for (var view : List.copyOf(showing.values())) {
            if (!view.player.isOnline()) {
                showing.remove(view.player.getUniqueId());
                continue;
            }
            view.age++;
            view.lineAge++;
            int before = (int) view.revealed;
            if (!view.pageRevealed()) {
                view.revealed = Math.min(DialoguePanel.length(view.current()), view.revealed + view.rate);
                if (view.line.voice().isEmpty() && (int) view.revealed / TYPE_EVERY > before / TYPE_EVERY) {
                    StorySound.DIALOGUE_TYPE.play(view.player, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.25f);
                }
            }
            if (lingeredOut(view)) {
                read(view);
                continue;
            }
            draw(view, view.age % REFRESH_TICKS == 0);
        }
    }

    /** A line without answers also closes once the player has walked away from where the conversation began. */
    private boolean lingeredOut(Showing view) {
        if (!view.answers.isEmpty()) return false;
        var at = view.player.getLocation();
        if (!at.getWorld().equals(view.openedAt.getWorld()) || at.distanceSquared(view.openedAt) > LEAVE * LEAVE) return true;
        if (settings.lingerTicks() <= 0 || !view.pageRevealed() || view.page < view.pages.size() - 1) return false;
        int spoken = view.line.voice().map(Voices.Voice::ticks).orElse(0);
        return view.lineAge >= Math.max(spoken, (int) Math.ceil(view.revealed / view.rate)) + settings.lingerTicks();
    }

    private void draw(Showing view, boolean always) {
        var hints = settings.hints();
        boolean last = view.page == view.pages.size() - 1;
        String hint = view.choosing() ? hints.choose() : !view.pageRevealed() || view.answered ? "" : last && view.answers.isEmpty() ? hints.close() : hints.advance();
        var panel = DialoguePanel.render(new DialoguePanel.View(view.line.speaker(),
                DialoguePanel.revealed(view.current(), (int) view.revealed), view.pageRevealed() && !view.choosing() && !view.answered,
                view.choosing() || view.answered ? view.answers : List.of(), view.selected, hint,
                Math.max(0, RISE - view.age * 2), view.age / 8 % 2 == 0),
                new DialoguePanel.Size(view.current().size(), last ? view.answers.size() : 0, true));
        if (always || !panel.equals(view.drawn)) {
            view.player.sendActionBar(panel);
            view.drawn = panel;
        }
    }

    private void advance(Showing view) {
        if (view.answered) return;
        if (!view.pageRevealed()) {
            view.revealed = DialoguePanel.length(view.current());
            StorySound.DIALOGUE_ADVANCE.play(view.player);
        } else if (view.page < view.pages.size() - 1) {
            view.page++;
            view.revealed = 0;
            StorySound.DIALOGUE_ADVANCE.play(view.player);
        } else if (!view.answers.isEmpty()) {
            choose(view, view.selected);
            return;
        } else {
            StorySound.DIALOGUE_ADVANCE.play(view.player);
            read(view);
            return;
        }
        draw(view, false);
    }

    private void choose(Showing view, int index) {
        view.selected = index;
        view.answered = true;
        StorySound.DIALOGUE_CHOOSE.play(view.player);
        draw(view, false);
        view.reply.chosen(index);
    }

    private void read(Showing view) {
        var reply = view.reply;
        view.answered = true;
        reply.read();
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        var view = showing.get(event.getPlayer().getUniqueId());
        if (view == null) return;
        boolean sneak = event.getInput().isSneak();
        if (sneak && !view.sneaking) advance(view);
        view.sneaking = sneak;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSlot(PlayerItemHeldEvent event) {
        var view = showing.get(event.getPlayer().getUniqueId());
        if (view == null) return;
        event.setCancelled(true);
        if (!view.choosing()) return;
        int slot = event.getNewSlot();
        if (slot < view.answers.size()) {
            choose(view, slot);
            return;
        }
        int step = Math.floorMod(slot - event.getPreviousSlot(), 9) <= 4 ? 1 : -1;
        view.selected = Math.floorMod(view.selected + step, view.answers.size());
        StorySound.DIALOGUE_SELECT.play(view.player, 1 + view.selected * 0.06f);
        draw(view, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        task.cancel();
        List.copyOf(showing.values()).forEach(view -> close(view.player));
        HandlerList.unregisterAll(this);
    }
}
