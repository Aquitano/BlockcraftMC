package me.blockcraft.story.dialogue;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import me.blockcraft.api.scene.Scenes;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A conversation in the panel above the hotbar, independent of whatever runs the conversation. A page types out at
 * an even pace in {@link Typing#ticks}, ahead of its recording; sneak shows the rest of a page, turns the page,
 * confirms the highlighted answer or closes a line without answers. The number keys pick an answer at once and the
 * scroll wheel moves the highlight: while the panel is open the hotbar rests on a slot whose neighbours lie beyond the
 * numbered answers, so a scroll and a number key never mean the same slot.
 * <p>
 * Party members near the player who talks watch the same panel and hear the same voice, but only that player answers.
 * They join when a line starts and drop out when they walk away, start a conversation of their own or watch a scene.
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

    /** A page types out evenly over {@code ticks}, but never slower than {@code slowest} characters a tick. */
    public record Typing(int ticks, double slowest) {
        public static Typing from(ConfigurationSection section) {
            return new Typing(Math.max(1, section.getInt("typing-ticks")), Math.max(0.1, section.getDouble("slowest-characters-per-tick")));
        }

        public double rate(int characters) {
            return Math.max(slowest, (double) characters / ticks);
        }
    }

    public record Settings(Typing typing, int lingerTicks, int restingSlot, double audienceRadius, Hints hints) {
        /** {@code watching} is the hint a party member sees while {@code speaker} talks and answers. */
        public record Hints(String advance, String choose, String close, Function<Player, String> watching) { }

        public static Settings from(ConfigurationSection section, Hints hints) {
            return new Settings(Typing.from(section), section.getInt("linger-seconds") * 20, section.getInt("resting-slot"),
                    section.getDouble("audience-radius"), hints);
        }
    }

    private static final int RISE = 6;
    /** How much further than the radius they joined within a member may wander before they stop watching. */
    private static final double AUDIENCE_SLACK = 4;
    private static final int TYPE_EVERY = 3;
    private static final int REFRESH_TICKS = 20;
    private static final double LEAVE = 6;

    private final Settings settings;
    private final Supplier<? extends Collection<Player>> party;
    private final Map<UUID, Showing> showing = new HashMap<>();
    private final Map<UUID, Showing> watching = new HashMap<>();
    private final BukkitTask task;

    private final class Showing {
        final Player player;
        final int previousSlot;
        final Location openedAt;
        final Set<Player> audience = new LinkedHashSet<>();
        Line line;
        List<List<String>> pages;
        List<String> answers;
        Reply reply;
        int page;
        double revealed;
        int age;
        int lineAge;
        int selected;
        boolean answered;
        boolean sneaking;
        Component drawn;
        Component drawnForAudience;

        Showing(Player player) {
            this.player = player;
            this.previousSlot = player.getInventory().getHeldItemSlot();
            this.openedAt = player.getLocation();
        }

        List<String> current() {
            return pages.get(page);
        }

        double rate() {
            return settings.typing().rate(DialoguePanel.length(current()));
        }

        boolean pageRevealed() {
            return revealed >= DialoguePanel.length(current());
        }

        boolean choosing() {
            return !answered && !answers.isEmpty() && pageRevealed() && page == pages.size() - 1;
        }
    }

    public DialogueHud(Plugin plugin, Settings settings, Supplier<? extends Collection<Player>> party) {
        this.settings = settings;
        this.party = party;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public boolean showing(Player player) {
        return showing.containsKey(player.getUniqueId());
    }

    /** Whether the line on {@code player}'s panel, their own or one they watch, is still typing out or being spoken. */
    public boolean speaking(Player player) {
        var view = showing.getOrDefault(player.getUniqueId(), watching.get(player.getUniqueId()));
        return view != null && !view.answered
                && (!view.pageRevealed() || view.lineAge < view.line.voice().map(Voices.Voice::ticks).orElse(0));
    }

    /** Shows {@code line} with {@code answers} to choose from once it is read; no answers means it closes when read. */
    public void say(Player player, Line line, List<String> answers, Reply reply) {
        stopWatching(player);
        var view = showing.get(player.getUniqueId());
        if (view == null) {
            view = new Showing(player);
            showing.put(player.getUniqueId(), view);
            player.getInventory().setHeldItemSlot(settings.restingSlot());
            StorySound.DIALOGUE_OPEN.play(player);
        } else {
            var previous = view.line;
            listeners(view).forEach(listener -> silence(listener, previous));
        }
        gather(view);
        view.line = line;
        view.pages = DialoguePanel.pages(line.text());
        view.answers = answers.stream().limit(DialoguePanel.MAX_ANSWERS).toList();
        view.reply = reply;
        view.page = 0;
        view.revealed = 0;
        view.lineAge = 0;
        view.selected = 0;
        view.answered = false;
        var listeners = listeners(view);
        line.voice().ifPresent(voice -> listeners.forEach(listener ->
                listener.playSound(Sound.sound(voice.sound(), Sound.Source.VOICE, 1, 1), Sound.Emitter.self())));
        draw(view, true);
    }

    public void close(Player player) {
        var view = showing.remove(player.getUniqueId());
        if (view == null) return;
        List.copyOf(view.audience).forEach(member -> leave(view, member));
        silence(player, view.line);
        if (player.isOnline()) {
            player.getInventory().setHeldItemSlot(view.previousSlot);
            player.sendActionBar(Component.empty());
            StorySound.DIALOGUE_CLOSE.play(player);
        }
        view.reply.closed();
    }

    /** The members near {@code view}'s speaker who are free to watch: not talking, not watching anyone else, not in a scene. */
    private void gather(Showing view) {
        var at = view.player.getLocation();
        for (var member : party.get()) {
            if (member.equals(view.player) || view.audience.contains(member) || !member.isOnline() || !member.getWorld().equals(at.getWorld())
                    || member.getLocation().distanceSquared(at) > settings.audienceRadius() * settings.audienceRadius()
                    || showing.containsKey(member.getUniqueId()) || watching.containsKey(member.getUniqueId()) || Scenes.isViewing(member)) {
                continue;
            }
            view.audience.add(member);
            watching.put(member.getUniqueId(), view);
            StorySound.DIALOGUE_OPEN.play(member);
        }
    }

    private boolean strayed(Showing view, Player member) {
        var at = view.player.getLocation();
        double reach = settings.audienceRadius() + AUDIENCE_SLACK;
        return !member.isOnline() || !member.getWorld().equals(at.getWorld()) || member.getLocation().distanceSquared(at) > reach * reach
                || Scenes.isViewing(member);
    }

    private void stopWatching(Player member) {
        var view = watching.get(member.getUniqueId());
        if (view != null) leave(view, member);
    }

    private void leave(Showing view, Player member) {
        view.audience.remove(member);
        watching.remove(member.getUniqueId(), view);
        if (!member.isOnline()) return;
        silence(member, view.line);
        if (!Scenes.isViewing(member)) member.sendActionBar(Component.empty());
        StorySound.DIALOGUE_CLOSE.play(member);
    }

    private static void silence(Player listener, Line line) {
        line.voice().ifPresent(voice -> listener.stopSound(SoundStop.named(voice.sound())));
    }

    private static List<Player> listeners(Showing view) {
        var listeners = new ArrayList<Player>(view.audience.size() + 1);
        listeners.add(view.player);
        listeners.addAll(view.audience);
        return listeners;
    }

    private void tick() {
        for (var view : List.copyOf(showing.values())) {
            if (!view.player.isOnline()) {
                showing.remove(view.player.getUniqueId());
                continue;
            }
            view.age++;
            view.lineAge++;
            for (var member : List.copyOf(view.audience)) {
                if (strayed(view, member)) leave(view, member);
            }
            int before = (int) view.revealed;
            if (!view.pageRevealed()) {
                view.revealed = Math.min(DialoguePanel.length(view.current()), view.revealed + view.rate());
                if (view.line.voice().isEmpty() && (int) view.revealed / TYPE_EVERY > before / TYPE_EVERY) {
                    float pitch = 0.9f + ThreadLocalRandom.current().nextFloat() * 0.25f;
                    listeners(view).forEach(listener -> StorySound.DIALOGUE_TYPE.play(listener, pitch));
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
        return view.lineAge >= Math.max(spoken, (int) Math.ceil(view.revealed / view.rate())) + settings.lingerTicks();
    }

    private void draw(Showing view, boolean always) {
        var hints = settings.hints();
        boolean last = view.page == view.pages.size() - 1;
        String hint = view.choosing() ? hints.choose() : !view.pageRevealed() || view.answered ? "" : last && view.answers.isEmpty() ? hints.close() : hints.advance();
        var panel = panel(view, hint, view.pageRevealed() && !view.choosing() && !view.answered);
        if (always || !panel.equals(view.drawn)) {
            view.player.sendActionBar(panel);
            view.drawn = panel;
        }
        if (view.audience.isEmpty()) return;
        var watched = panel(view, view.choosing() ? hints.watching().apply(view.player) : "", false);
        if (always || !watched.equals(view.drawnForAudience)) {
            view.audience.forEach(member -> member.sendActionBar(watched));
            view.drawnForAudience = watched;
        }
    }

    private Component panel(Showing view, String hint, boolean more) {
        boolean last = view.page == view.pages.size() - 1;
        return DialoguePanel.render(new DialoguePanel.View(view.line.speaker(),
                DialoguePanel.revealed(view.current(), (int) view.revealed), more,
                view.choosing() || view.answered ? view.answers : List.of(), view.selected, hint,
                Math.max(0, RISE - view.age * 2), view.age / 8 % 2 == 0),
                new DialoguePanel.Size(view.current().size(), last ? view.answers.size() : 0, true));
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
        stopWatching(event.getPlayer());
        var view = showing.remove(event.getPlayer().getUniqueId());
        if (view != null) List.copyOf(view.audience).forEach(member -> leave(view, member));
    }

    @Override
    public void close() {
        task.cancel();
        List.copyOf(showing.values()).forEach(view -> close(view.player));
        HandlerList.unregisterAll(this);
    }
}
