package me.blockcraft.sternenfront.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.sternenfront.arena.ArenaFile;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.BattlePlan;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Deployment;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Fighter;
import me.blockcraft.sternenfront.game.Posts;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static me.blockcraft.sternenfront.menu.DeployScreen.BUTTONS;
import static me.blockcraft.sternenfront.menu.DeployScreen.CLASS_CARDS;
import static me.blockcraft.sternenfront.menu.DeployScreen.SPECIALS;

/**
 * Where a dead player chooses whom to deploy as and where, on one screen: the class cards, the reinforcement and the
 * heroes in the chest, the spawns on a map in the player's own inventory, which the menu borrows while it is open. The
 * pack draws {@link DeployScreen} around it. That picture is fixed while the chest is open, so a choice or a change
 * it shows, such as the player's battle points, opens the menu again with a new one. A spawn locking, opening or
 * becoming the recommendation must hold for a while first, since an enemy at a post's edge flips it back and forth and
 * every reopening swallows a click; its marker shows the change at once.
 */
public final class DeployMenu extends Menu {

    private static final int ROWS = 6;
    private static final int CLOSE = BUTTONS.get(0);
    private static final int DEPLOY = BUTTONS.get(1);
    /** The map's top-left cell, the first slot the menu borrows from the player. */
    private static final int MAP = ROWS * 9;
    private static final long TICKS_PER_SECOND = 20;
    private static final int MAX_COUNTDOWN = 99;
    private static final double TO_HEARTS = 2;
    private static final int WHITE = 0xFFFFFF;

    private final SternenfrontGame game;
    private final Player viewer;
    private final Optional<DeployScreen.View> shown;
    private final Map<Spawn, Integer> mapSlots;
    private final long settleTicks;
    /** A change of spawn marks the title does not show yet, and since when it holds. */
    private Optional<DeployScreen.View> unsettled = Optional.empty();
    private long unsettledSince;

    public DeployMenu(SternenfrontGame game, Player viewer) {
        super(ROWS, game.messages().get("deploy.title"), true);
        this.game = game;
        this.viewer = viewer;
        this.shown = view();
        this.mapSlots = shown.map(view -> mapSlots(view.faction())).orElse(Map.of());
        this.settleTicks = Math.clamp(game.plugin().getConfig().getInt("respawn.screen-settle-ticks"), 0, 200);
        area(CLOSE, DeployScreen.buttonColumns(0), 1);
        area(DEPLOY, DeployScreen.buttonColumns(1), 1);
        CLASS_CARDS.forEach(slot -> area(slot, DeployScreen.CLASS_CARD_COLUMNS, DeployScreen.CLASS_CARD_ROWS));
        SPECIALS.forEach(slot -> area(slot, DeployScreen.SPECIAL_COLUMNS, 1));
        draw();
    }

    @Override
    protected Optional<Component> screen() {
        return shown.map(view -> DeployScreen.title(game.messages(), view));
    }

    /** Shows what changed while the player looks at this menu; a closed one stays closed until they open it again. */
    public void refresh() {
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
            draw();
        }
    }

    private void draw() {
        var current = view();
        if (current.isEmpty()) {
            return;
        }
        var view = current.get();
        if (reopens(view)) {
            game.deployment().openDeploy(viewer);
            return;
        }
        var faction = view.faction();
        var messages = game.messages();
        var deployment = game.deployment();
        boolean confirmed = game.fighter(viewer).choosing().filter(Fighter.Choosing::confirmed).isPresent();
        var suggested = deployment.suggested(viewer);
        var pick = view.specials().stream().filter(DeployScreen.Special::selected).findFirst();
        var paid = pick.filter(DeployScreen.Special::open);
        boolean unavailable = pick.isPresent() && paid.isEmpty();
        var role = Component.text(view.role().name(), NamedTextColor.WHITE);
        var summary = new ArrayList<>(List.of(deployment.status(viewer), role));
        paid.ifPresent(special -> summary.add(messages.get("deploy.cost", Formatter.number("cost", special.cost()))));
        if (unavailable) summary.add(messages.get("deploy.paid-unavailable"));
        if (confirmed || !unavailable) summary.add(messages.get(confirmed ? "deploy.queued" : paid.isPresent() ? "deploy.confirm-paid" : "deploy.quick-lore"));
        if (!confirmed) summary.add(messages.get("deploy.auto-free"));
        if (unavailable || confirmed && paid.isPresent()) summary.add(messages.get("deploy.paid-fallback"));
        set(CLOSE, MenuIcon.TOOL_CLOSE.item().name(messages.get("deploy.close")).lore(messages.getLines("deploy.close-lore")).build());
        set(DEPLOY, MenuIcon.DEPLOY.item().name(messages.get("deploy.deploy", Placeholder.component("target", targetName(suggested))))
                .lore(summary).build());
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length && index < CLASS_CARDS.size(); index++) {
            boolean selected = view.classes().get(index).selected();
            set(CLASS_CARDS.get(index), game.loadouts().card(faction, classes[index], selected,
                    List.of(messages.get(selected ? "class.selected" : "class.select"))));
        }
        set(SPECIALS.getFirst(), game.reinforcementUnits().card(viewer, faction, Reinforcement.of(faction),
                view.specials().getFirst().selected(), confirmed));
        var heroes = Hero.of(faction);
        for (int index = 0; index < heroes.size() && index + 1 < SPECIALS.size(); index++) {
            set(SPECIALS.get(index + 1), game.heroes().card(viewer, faction, heroes.get(index),
                    view.specials().get(index + 1).selected(), confirmed));
        }
        var cost = paid.map(special -> messages.get("deploy.spawn-cost", Placeholder.unparsed("unit", special.name()),
                Formatter.number("cost", special.cost())));
        var places = view.places().stream().collect(Collectors.toMap(DeployScreen.Place::spawn, place -> place));
        mapSlots.forEach((spawn, slot) -> set(slot, Optional.ofNullable(places.get(spawn))
                .map(place -> marker(faction, place, suggested, cost)).orElse(null)));
    }

    /**
     * Whether the title no longer shows {@code current}: at once for the player's own choices, and for a change of spawn
     * marks once it held for the settle time.
     */
    private boolean reopens(DeployScreen.View current) {
        if (shown.filter(current::equals).isPresent()) {
            unsettled = Optional.empty();
            return false;
        }
        if (shown.map(DeployScreen.View::withoutSpawnMarks).filter(current.withoutSpawnMarks()::equals).isEmpty()) {
            return true;
        }
        if (unsettled.filter(current::equals).isEmpty()) {
            unsettled = Optional.of(current);
            unsettledSince = game.tick();
        }
        return game.tick() - unsettledSince >= settleTicks;
    }

    /**
     * What the screen shows that changes only on the player's own choices or seldom: who they deploy as, which cards
     * they could pay for, their battle points, and which spawns there are with the marks their markers wear.
     */
    private Optional<DeployScreen.View> view() {
        var faction = game.factionOf(viewer);
        if (faction.isEmpty()) {
            return Optional.empty();
        }
        var side = faction.get();
        var fighter = game.fighter(viewer);
        var picked = fighter.choosing().flatMap(Fighter.Choosing::hero);
        var pickedUnit = fighter.choosing().flatMap(Fighter.Choosing::reinforcement);
        int points = game.heroes().points(viewer);
        var role = picked.map(hero -> heroRole(side, hero, points))
                .or(() -> pickedUnit.map(unit -> unitRole(side, unit, points)))
                .orElseGet(() -> classRole(side, fighter.combatClass(), points));
        var cards = new ArrayList<DeployScreen.ClassCard>();
        for (var combatClass : CombatClass.values()) {
            cards.add(new DeployScreen.ClassCard(plain(combatClass.messageKey(side) + ".short"), DeployScreen.Portrait.of(side, combatClass),
                    combatClass.abilities(side).stream().map(ability -> ability.icon()).toList(),
                    picked.isEmpty() && pickedUnit.isEmpty() && fighter.combatClass() == combatClass));
        }
        var unit = Reinforcement.of(side);
        var specials = new ArrayList<>(List.of(new DeployScreen.Special(plain("reinforcement." + unit.key() + ".short"),
                DeployScreen.Portrait.of(unit), game.reinforcementUnits().cost(unit),
                game.reinforcementUnits().roster().affordable(viewer.getUniqueId(), side, unit), pickedUnit.filter(unit::equals).isPresent())));
        for (var hero : Hero.of(side)) {
            specials.add(new DeployScreen.Special(plain("hero." + hero.key() + ".short"), DeployScreen.Portrait.of(hero),
                    game.heroes().cost(side), game.heroes().affordable(viewer, side, hero), picked.filter(hero::equals).isPresent()));
        }
        var recommended = game.deployment().recommended(viewer);
        var places = new ArrayList<DeployScreen.Place>();
        places.add(place(side, new Spawn.Headquarters(), plain("deploy.headquarters"), recommended));
        for (int index : game.posts().indices()) {
            places.add(place(side, new Spawn.Post(index), plain("deploy.screen.post", game.posts().name(index)), recommended));
        }
        game.front().ifPresent(front -> places.add(place(side, new Spawn.Dropship(), plain("deploy.screen.dropship",
                Placeholder.unparsed("letter", Posts.letter(front.post()))), recommended)));
        var buttons = List.of(plain("deploy.screen.close"), plain("deploy.screen.deploy"));
        return Optional.of(new DeployScreen.View(side, game.messages().get("deploy.title"), buttons, role, cards, specials, places,
                hint(specials)));
    }

    private DeployScreen.Place place(Faction side, Spawn spawn, String name, Spawn recommended) {
        var choosing = game.fighter(viewer).choosing();
        boolean open = game.deployment().available(side, spawn);
        var selection = choosing.flatMap(Fighter.Choosing::chosen).filter(spawn::equals).isEmpty() ? DeployScreen.Selection.NONE
                : choosing.filter(Fighter.Choosing::confirmed).isPresent() ? DeployScreen.Selection.CONFIRMED : DeployScreen.Selection.PENDING;
        return new DeployScreen.Place(spawn, name, colour(side, spawn), open, open && spawn.equals(recommended), selection);
    }

    /** Why the last click on a spawn was refused, else what a click on one costs while a paid pick waits, else how to choose. */
    private DeployScreen.Hint hint(List<DeployScreen.Special> specials) {
        var paid = specials.stream().filter(special -> special.selected() && special.open()).findFirst();
        return game.deployment().refusal(viewer)
                .map(reason -> new DeployScreen.Hint(PlainTextComponentSerializer.plainText().serialize(reason), DeployScreen.Hint.Tone.REFUSED))
                .or(() -> paid.map(special -> new DeployScreen.Hint(plain("deploy.screen.hint-paid", Placeholder.unparsed("unit", special.name()),
                        Formatter.number("cost", special.cost())), DeployScreen.Hint.Tone.COST)))
                .orElseGet(() -> new DeployScreen.Hint(plain("deploy.screen.hint"), DeployScreen.Hint.Tone.HELP));
    }

    /** The colour of a spawn's badge: its owner's, white for a neutral post or the dropship. */
    private int colour(Faction faction, Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> faction.text().value();
            case Spawn.Post post -> game.posts().owner(post.index()).map(owner -> owner.text().value()).orElse(WHITE);
            case Spawn.Dropship ignored -> WHITE;
        };
    }

    private DeployScreen.Role classRole(Faction faction, CombatClass combatClass, int points) {
        var body = game.config().stats(faction, combatClass);
        var blaster = combatClass.blaster(faction);
        var stats = new ArrayList<>(List.of(hearts("health", body.health()), percent("speed", body.speed())));
        stats.addAll(blasterStats(blaster));
        return new DeployScreen.Role(plain("deploy.screen.kind.class"), plain(combatClass.messageKey(faction) + ".name"),
                DeployScreen.Portrait.of(faction, combatClass), stats, plain("blaster." + blaster.key() + ".name"),
                abilities(combatClass.abilities(faction).stream().map(ability -> new Named(ability.messageKey(faction), ability.icon())).toList()),
                price(Optional.empty(), points));
    }

    private DeployScreen.Role unitRole(Faction faction, Reinforcement unit, int points) {
        var body = game.reinforcementUnits().stats(unit);
        var stats = new ArrayList<>(List.of(hearts("health", body.health()), percent("speed", body.speed())));
        stats.addAll(blasterStats(unit.blaster()));
        return new DeployScreen.Role(plain("deploy.screen.kind.reinforcement"), plain("reinforcement." + unit.key() + ".name"),
                DeployScreen.Portrait.of(unit), stats, plain("blaster." + unit.blaster().key() + ".name"),
                abilities(unit.abilities().stream().map(ability -> new Named(ability.messageKey(faction), ability.icon())).toList()),
                price(Optional.of(body.cost()), points));
    }

    /** A hero moves at the natural pace; a blade reaches no farther than a swing, so its range is a dash. */
    private DeployScreen.Role heroRole(Faction faction, Hero hero, int points) {
        var stats = new ArrayList<>(List.of(hearts("health", game.heroes().config().health(hero)), percent("speed", 1)));
        String weapon = switch (hero.weapon()) {
            case Hero.Weapon.Gun gun -> {
                stats.addAll(blasterStats(gun.blaster()));
                yield plain("blaster." + gun.blaster().key() + ".name");
            }
            case Hero.Weapon.Saber ignored -> {
                stats.addAll(List.of(hearts("damage", game.heroes().config().saber().damage()), none("range")));
                yield plain("hero." + hero.key() + ".weapon");
            }
            case Hero.Weapon.RelicBlade ignored -> {
                stats.addAll(List.of(none("damage"), none("range")));
                yield plain("relic.name");
            }
        };
        return new DeployScreen.Role(plain("deploy.screen.kind.hero"), plain("hero." + hero.key() + ".name"),
                DeployScreen.Portrait.of(hero), stats, weapon,
                abilities(hero.abilities().stream().map(ability -> new Named("hero.ability." + ability.key(), ability.icon())).toList()),
                price(Optional.of(game.heroes().cost(faction)), points));
    }

    private record Named(String key, AbilityIcon icon) { }

    private List<DeployScreen.Ability> abilities(List<Named> named) {
        var keys = Deployment.CONTROL_KEYS;
        var abilities = new ArrayList<DeployScreen.Ability>();
        for (int index = 0; index < named.size() && index < keys.size(); index++) {
            abilities.add(new DeployScreen.Ability(plain(named.get(index).key() + ".name"),
                    plain("deploy.screen.ability-key", Placeholder.unparsed("key", keys.get(index))), named.get(index).icon()));
        }
        return abilities;
    }

    private List<DeployScreen.Stat> blasterStats(Blaster blaster) {
        var stats = game.config().stats(blaster);
        return List.of(hearts("damage", stats.damage() * stats.bolts()),
                new DeployScreen.Stat(plain("deploy.screen.stat.range"), plain("deploy.screen.value.metres",
                        Formatter.number("value", Math.round(stats.range()))), false));
    }

    private DeployScreen.Stat hearts(String stat, double health) {
        return new DeployScreen.Stat(plain("deploy.screen.stat." + stat), plain("deploy.screen.value.hearts",
                Formatter.number("value", health / TO_HEARTS)), true);
    }

    private DeployScreen.Stat none(String stat) {
        return new DeployScreen.Stat(plain("deploy.screen.stat." + stat), plain("deploy.screen.value.none"), false);
    }

    private DeployScreen.Stat percent(String stat, double share) {
        return new DeployScreen.Stat(plain("deploy.screen.stat." + stat), plain("deploy.screen.value.percent",
                Formatter.number("value", Math.round(share * 100))), false);
    }

    private List<DeployScreen.Stat> price(Optional<Integer> cost, int points) {
        return List.of(new DeployScreen.Stat(plain("deploy.screen.cost"), cost.map(value -> plain("deploy.screen.points-value",
                        Formatter.number("points", value))).orElseGet(() -> plain("deploy.screen.free")), false),
                new DeployScreen.Stat(plain("deploy.screen.balance"), plain("deploy.screen.points-value", Formatter.number("points", points)), false));
    }

    private String plain(String key, TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(game.messages().get(key, resolvers));
    }

    /** Posts and headquarters where the map puts them, north up and east right, and the dropship in its own cell. */
    private Map<Spawn, Integer> mapSlots(Faction faction) {
        var markers = new ArrayList<TacticalLayout.Marker>();
        var headquarters = game.map().headquarters().get(faction).zone().getCenter();
        markers.add(new TacticalLayout.Marker(new Spawn.Headquarters(), new BattlePlan.Point(headquarters.getX(), headquarters.getZ())));
        for (int index : game.posts().indices()) {
            var center = game.map().posts().get(index).center();
            markers.add(new TacticalLayout.Marker(new Spawn.Post(index), new BattlePlan.Point(center.getX(), center.getZ())));
        }
        var slots = new HashMap<>(TacticalLayout.project(markers, MAP));
        slots.put(new Spawn.Dropship(), MAP + TacticalLayout.DROPSHIP_CELL);
        return Map.copyOf(slots);
    }

    /**
     * A spawn's marker. The pack frames the chosen spawn, gold while it waits for a confirmation and green once
     * confirmed, marks the recommended spawn with a star and a locked one with a padlock. The spawn the player will
     * land at counts down the seconds to it. Without the pack its block says what the badge says: a banner for a spawn,
     * glass for a locked one, concrete for a contested one.
     */
    private ItemStack marker(Faction faction, DeployScreen.Place place, Spawn suggested, Optional<Component> cost) {
        var messages = game.messages();
        var deployment = game.deployment();
        var spawn = place.spawn();
        boolean available = place.open();
        var owner = spawn instanceof Spawn.Post post ? game.posts().owner(post.index()) : Optional.of(faction);
        boolean embattled = spawn instanceof Spawn.Post post
                && (game.posts().contested(post.index()) || owner.filter(faction::equals).isPresent() && !available);
        boolean selected = place.selection() != DeployScreen.Selection.NONE;
        boolean confirmed = place.selection() == DeployScreen.Selection.CONFIRMED;
        boolean recommended = place.recommended();
        long countdown = confirmed ? deployment.waitSeconds(viewer) : deployment.autoSeconds(viewer);
        boolean landing = available && spawn.equals(suggested) && countdown > 0;
        var lore = new ArrayList<Component>();
        if (!available) lore.add(messages.get("deploy.locked"));
        lore.addAll(state(faction, spawn, owner, available, embattled));
        if (recommended) lore.add(messages.get("deploy.recommended"));
        if (landing) lore.add(messages.get("deploy.countdown", Formatter.number("seconds", countdown)));
        if (available) lore.add(messages.get(!selected ? "deploy.choose" : confirmed ? "deploy.chosen" : "deploy.pending"));
        if (available) cost.ifPresent(lore::add);
        var block = spawn instanceof Spawn.Dropship ? Material.FIREWORK_ROCKET : Material.valueOf(owner.map(Faction::blocks)
                .orElse(Faction.NEUTRAL).name() + (embattled ? "_CONCRETE" : available ? "_BANNER" : "_STAINED_GLASS_PANE"));
        var item = Items.of(block).name(spawnName(spawn)).lore(lore).hideAttributes()
                .state(!available ? MenuState.LOCKED : confirmed ? MenuState.ACTIVE : selected ? MenuState.PENDING : MenuState.AVAILABLE);
        if (confirmed && available) {
            item.glow();
        }
        var marker = item.build();
        if (landing) {
            marker.setData(DataComponentTypes.MAX_STACK_SIZE, MAX_COUNTDOWN);
            marker.setAmount((int) Math.min(MAX_COUNTDOWN, countdown));
        }
        var look = CustomModelData.customModelData()
                .addString(available && selected ? confirmed ? "chosen" : "pending" : "")
                .addString(!available ? "locked" : recommended ? "recommended" : "");
        switch (spawn) {
            case Spawn.Post post when post.index() < ArenaFile.MAX_POSTS -> {
                boolean filled = embattled ? game.tick() / TICKS_PER_SECOND % 2 == 0 : available;
                Items.of(marker).model(Key.key("blockcraft", "sternenfront/post_" + (char) ('a' + post.index())
                        + (filled ? "_filled" : "_hollow")));
                look.addColor(Color.fromRGB(place.colour()));
            }
            case Spawn.Post ignored -> {
                return marker;
            }
            case Spawn.Headquarters ignored -> Items.of(marker).model(Key.key("blockcraft",
                    "sternenfront/spawn_headquarters_" + faction.blocks().name().toLowerCase(Locale.ROOT)));
            case Spawn.Dropship ignored -> Items.of(marker).model(Key.key("blockcraft", "sternenfront/spawn_dropship"));
        }
        marker.setData(DataComponentTypes.CUSTOM_MODEL_DATA, look.build());
        return marker;
    }

    /** Who holds a post and whether it is fought over, or why the dropship is closed; the headquarters is always open. */
    private List<Component> state(Faction faction, Spawn spawn, Optional<Faction> owner, boolean available, boolean embattled) {
        var messages = game.messages();
        return switch (spawn) {
            case Spawn.Headquarters ignored -> List.of();
            case Spawn.Post ignored -> {
                var lines = new ArrayList<>(List.of(messages.get(owner.isEmpty() ? "deploy.post-neutral"
                        : owner.get() == faction ? "deploy.post-own" : "deploy.post-enemy")));
                if (embattled) lines.add(messages.get("deploy.post-contested"));
                yield lines;
            }
            case Spawn.Dropship ignored -> available ? List.of() : List.of(messages.get("deploy.dropship-locked"));
        };
    }

    private Component targetName(Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> game.messages().get("deploy.target-headquarters");
            case Spawn.Post post -> game.messages().get("deploy.target-post", game.posts().name(post.index()));
            case Spawn.Dropship ignored -> game.front().map(front -> game.messages().get("deploy.target-dropship", game.posts().name(front.post())))
                    .orElseGet(() -> game.messages().get("deploy.dropship-plain"));
        };
    }

    private Component spawnName(Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> game.messages().get("deploy.headquarters");
            case Spawn.Post post -> game.messages().get("deploy.post", game.posts().name(post.index()));
            case Spawn.Dropship ignored -> game.front().map(front -> game.messages().get("deploy.dropship", game.posts().name(front.post())))
                    .orElseGet(() -> game.messages().get("deploy.dropship-plain"));
        };
    }

    /** A card plays the sound of its own outcome, and a locked spawn its refusal. */
    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        var faction = game.factionOf(viewer);
        return !CLASS_CARDS.contains(slot) && !SPECIALS.contains(slot) && spawnAt(slot)
                .filter(spawn -> faction.filter(side -> !game.deployment().available(side, spawn)).isPresent()).isEmpty();
    }

    private Optional<Spawn> spawnAt(int slot) {
        return mapSlots.entrySet().stream().filter(entry -> entry.getValue() == slot).map(Map.Entry::getKey).findFirst();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == CLOSE) {
            Menu.close(player);
            return;
        }
        if (!game.deployment().deciding(player)) {
            return;
        }
        game.deployment().clearRefusal(player);
        if (slot == DEPLOY) {
            game.deployment().quickDeploy(player);
            return;
        }
        int classIndex = CLASS_CARDS.indexOf(slot);
        if (classIndex >= 0) {
            game.chooseClass(player, CombatClass.values()[classIndex]);
            return;
        }
        if (slot == SPECIALS.getFirst()) {
            game.factionOf(player).ifPresent(faction -> game.chooseReinforcement(player, Reinforcement.of(faction)));
            return;
        }
        int heroIndex = SPECIALS.indexOf(slot) - 1;
        var heroes = game.factionOf(player).map(Hero::of).orElse(List.of());
        if (heroIndex >= 0 && heroIndex < heroes.size()) {
            game.chooseHero(player, heroes.get(heroIndex));
            return;
        }
        spawnAt(slot).ifPresent(spawn -> game.deployment().choose(player, spawn));
    }
}
