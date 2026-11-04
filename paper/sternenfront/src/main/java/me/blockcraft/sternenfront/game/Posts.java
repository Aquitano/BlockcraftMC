package me.blockcraft.sternenfront.game;

import me.blockcraft.api.world.BlocksChangedEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.arena.FrontMap.PostSite;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.appearance.PostHologram;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The map's command posts while a round runs: each second the players in a post's zone move its capture, its
 * coloured blocks and hologram follow its owner, a label floats over it, and whoever stands in it sees its
 * progress as a boss bar.
 */
public final class Posts {

    public record Presence(int kolonien, int hegemonie) {

        int of(Faction faction) {
            return faction == Faction.KOLONIEN ? kolonien : hegemonie;
        }
    }

    /** What changed at a post this second, with the players of the faction that did it who stood in the zone. */
    public record Event(int index, CommandPost.Change change, List<Player> present) { }
    public record Alert(int index, Faction defender) { }
    public record Update(List<Event> changes, List<Alert> alerts) { }

    private static final int BAR_SEGMENTS = 10;
    private static final Color CARD = Color.fromARGB(96, 0, 0, 0);
    private static final List<DyeColor> LONGEST_FIRST = Arrays.stream(DyeColor.values())
            .sorted(Comparator.comparingInt((DyeColor dye) -> dye.name().length()).reversed()).toList();

    private final class Post {
        private final PostSite site;
        private final CommandPost logic;
        private final BossBar bar = BossBar.bossBar(Component.empty(), 0, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
        private final Set<UUID> viewers = new HashSet<>();
        private TextDisplay label;
        private PostHologram hologram;
        private Presence presence = new Presence(0, 0);
        private final PostThreat threat = new PostThreat();

        private Post(PostSite site, int captureSeconds) {
            this.site = site;
            this.logic = new CommandPost(captureSeconds);
        }
    }

    private final Messages messages;
    private final List<Post> posts;
    private OptionalInt priority = OptionalInt.empty();

    public Posts(Messages messages, List<PostSite> sites, int captureSeconds) {
        this.messages = messages;
        this.posts = sites.stream().map(site -> new Post(site, captureSeconds)).toList();
    }

    public int size() {
        return posts.size();
    }

    public static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    /** The post's letter and name for a message, as {@code <letter>} and {@code <post>}. */
    public TagResolver name(int index) {
        return TagResolver.resolver(Placeholder.unparsed("letter", letter(index)),
                Placeholder.component("post", messages.get("post." + posts.get(index).site.key())));
    }

    public Optional<Faction> owner(int index) {
        return posts.get(index).logic.owner();
    }

    public boolean contested(int index) {
        var presence = posts.get(index).presence;
        return CommandPost.contested(presence.kolonien(), presence.hegemonie());
    }

    /** Whether {@code faction} may deploy at the post: it holds it and no enemy stands in it. */
    public boolean safe(int index, Faction faction) {
        var post = posts.get(index);
        return post.logic.owner().filter(faction::equals).isPresent() && post.presence.of(faction.enemy()) == 0;
    }

    public List<Optional<Faction>> owners() {
        return posts.stream().map(post -> post.logic.owner()).toList();
    }

    public boolean priority(int index) {
        return priority.equals(OptionalInt.of(index));
    }

    public void priority(OptionalInt post) {
        priority = post;
    }

    /** Puts every post into its neutral colours and shows the labels. */
    public void show() {
        for (var post : posts) {
            recolour(post, Faction.NEUTRAL);
            post.label = post.site.label().getWorld().spawn(post.site.label(), TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setBillboard(Display.Billboard.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(CARD);
                display.setShadowed(true);
                display.setBrightness(Displays.LIT);
            });
            draw(post, posts.indexOf(post));
        }
    }

    public void holograms(Plugin plugin) {
        if (plugin.getConfig().getBoolean("presentation.holographic-posts", true)) {
            posts.forEach(post -> post.hologram = new PostHologram(plugin, post.site.center()));
        }
    }

    public void tick(long tick) {
        for (var post : posts) {
            if (post.hologram != null) {
                post.hologram.tick(tick, post.logic.owner(), post.logic.leaning(),
                        post.logic.leaning().map(post.logic::share).orElse(0.0),
                        CommandPost.contested(post.presence.kolonien(), post.presence.hegemonie()));
            }
        }
    }

    /** One second of the round for every post; {@code fighters} are the players on the field with their faction. */
    public Update second(Map<LivingEntity, Faction> fighters, long tick, int alertCooldownTicks) {
        var events = new ArrayList<Event>();
        var alerts = new ArrayList<Alert>();
        for (int index = 0; index < posts.size(); index++) {
            var post = posts.get(index);
            var inside = fighters.entrySet().stream().filter(entry -> post.site.contains(entry.getKey().getLocation()))
                    .collect(Collectors.groupingBy(Map.Entry::getValue, () -> new EnumMap<>(Faction.class),
                            Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
            var kolonien = inside.getOrDefault(Faction.KOLONIEN, List.of());
            var hegemonie = inside.getOrDefault(Faction.HEGEMONIE, List.of());
            post.presence = new Presence(kolonien.size(), hegemonie.size());
            int current = index;
            var threatened = post.logic.owner().filter(owner -> post.presence.of(owner.enemy()) > 0);
            post.threat.update(threatened, tick, alertCooldownTicks).ifPresent(owner -> alerts.add(new Alert(current, owner)));
            post.logic.update(kolonien.size(), hegemonie.size(), 1).ifPresent(change -> {
                var by = switch (change) {
                    case CommandPost.Change.Captured captured -> captured.by();
                    case CommandPost.Change.Neutralised neutralised -> neutralised.from().enemy();
                };
                recolour(post, change instanceof CommandPost.Change.Captured captured ? captured.by().blocks() : Faction.NEUTRAL);
                events.add(new Event(current, change, (by == Faction.KOLONIEN ? kolonien : hegemonie).stream()
                        .filter(Player.class::isInstance).map(Player.class::cast).toList()));
            });
            draw(post, index);
            var viewing = new HashSet<UUID>();
            inside.values().forEach(players -> players.stream().filter(Player.class::isInstance).map(Player.class::cast).forEach(player -> {
                viewing.add(player.getUniqueId());
                player.showBossBar(post.bar);
            }));
            post.viewers.stream().filter(id -> !viewing.contains(id)).map(Bukkit::getPlayer)
                    .filter(Objects::nonNull).forEach(player -> player.hideBossBar(post.bar));
            post.viewers.clear();
            post.viewers.addAll(viewing);
        }
        return new Update(List.copyOf(events), List.copyOf(alerts));
    }

    private void draw(Post post, int index) {
        var leaning = post.logic.leaning();
        double share = leaning.map(post.logic::share).orElse(0.0);
        var state = post.logic.owner().map(owner -> messages.get("post.held", faction(owner)))
                .orElseGet(() -> messages.get("post.neutral"));
        if (CommandPost.contested(post.presence.kolonien(), post.presence.hegemonie())) {
            state = messages.get("post.contested");
        }
        var tint = post.logic.owner().<TextColor>map(Faction::text).orElse(NamedTextColor.WHITE);
        var resolvers = TagResolver.resolver(name(index), Placeholder.component("state", state),
                Placeholder.styling("tint", tint), Placeholder.component("progress", bar(leaning, share)));
        if (post.label != null && post.label.isValid()) {
            post.label.text(messages.get("post.label", resolvers));
        }
        post.bar.name(messages.get(priority(index) ? "post.bar-priority" : "post.bar", resolvers)).progress((float) share).color(leaning.map(faction ->
                faction == Faction.KOLONIEN ? BossBar.Color.YELLOW : BossBar.Color.BLUE).orElse(BossBar.Color.WHITE));
    }

    private static Component bar(Optional<Faction> leaning, double share) {
        int filled = (int) Math.round(share * BAR_SEGMENTS);
        var colour = leaning.<TextColor>map(Faction::text).orElse(NamedTextColor.WHITE);
        return Component.text("▌".repeat(filled), colour)
                .append(Component.text("▌".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
    }

    private TagResolver faction(Faction faction) {
        return Placeholder.component("faction", messages.get("faction." + faction.key() + ".name").colorIfAbsent(faction.text()));
    }

    /** Swaps the dye of every coloured block of the post, keeping its shape, and tells the replay. */
    private static void recolour(Post post, DyeColor dye) {
        var changed = new ArrayList<Block>();
        for (var block : post.site.indicators()) {
            var name = block.getType().name();
            LONGEST_FIRST.stream().filter(colour -> name.startsWith(colour.name() + "_")).findFirst().ifPresent(colour -> {
                var target = Material.matchMaterial(dye.name() + name.substring(colour.name().length()));
                if (target != null && target != block.getType()) {
                    var data = target.createBlockData();
                    block.getBlockData().copyTo(data);
                    block.setBlockData(data, false);
                    changed.add(block);
                }
            });
        }
        if (!changed.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(changed));
        }
    }

    public void clear() {
        priority = OptionalInt.empty();
        for (var post : posts) {
            post.viewers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(player -> player.hideBossBar(post.bar));
            post.viewers.clear();
            if (post.label != null) {
                post.label.remove();
            }
            if (post.hologram != null) {
                post.hologram.close();
                post.hologram = null;
            }
        }
    }

    public List<Integer> indices() {
        return IntStream.range(0, posts.size()).boxed().toList();
    }
}
