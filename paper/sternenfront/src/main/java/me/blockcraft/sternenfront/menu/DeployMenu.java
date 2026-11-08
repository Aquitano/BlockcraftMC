package me.blockcraft.sternenfront.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.BattlePlan;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Fighter;
import me.blockcraft.sternenfront.game.Posts;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where a dead player chooses whom to deploy as and where, on one screen: the class cards, the reinforcement and the
 * heroes in the chest, the spawns on a map in the player's own inventory, which the menu borrows while it is open. The
 * pack draws {@link DeployScreen} around it. That picture is fixed while the chest is open, so a choice or a change
 * it shows, such as the player's battle points, opens the menu again with a new one.
 */
public final class DeployMenu extends Menu {

    private static final int ROWS = 6;
    private static final List<Integer> CLASS_CARDS = List.of(9, 14, 27, 32);
    private static final List<Integer> SPECIALS = List.of(45, 48, 51);
    /** The map's top-left cell, the first slot the menu borrows from the player. */
    private static final int MAP = ROWS * 9;
    /** The map's bottom-right cell, kept free of posts for the landing. */
    private static final int LANDING_SLOT = MAP + 26;
    private static final long TICKS_PER_SECOND = 20;
    private static final int MAX_COUNTDOWN = 99;
    private static final double TO_HEARTS = 2;

    private final SternenfrontGame game;
    private final Player viewer;
    private final Optional<DeployScreen.View> shown;
    private ItemStack status;
    private Map<Spawn, Integer> mapSlots = Map.of();

    public DeployMenu(SternenfrontGame game, Player viewer) {
        super(ROWS, game.messages().get("deploy.title"), true);
        this.game = game;
        this.viewer = viewer;
        this.shown = view();
        closeButton(ToolbarButton.LEFT);
        CLASS_CARDS.forEach(slot -> area(slot, 4, 2));
        SPECIALS.forEach(slot -> area(slot, 3, 1));
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
        var faction = game.factionOf(viewer);
        if (faction.isEmpty()) {
            return;
        }
        if (!view().equals(shown)) {
            game.deployment().openDeploy(viewer);
            return;
        }
        var messages = game.messages();
        var fighter = game.fighter(viewer);
        var chosen = fighter.choosing().flatMap(Fighter.Choosing::chosen);
        var picked = fighter.choosing().flatMap(Fighter.Choosing::hero);
        var pickedUnit = fighter.choosing().flatMap(Fighter.Choosing::reinforcement);
        boolean confirmed = fighter.choosing().filter(Fighter.Choosing::confirmed).isPresent();
        boolean affordablePick = picked.filter(hero -> game.heroes().affordable(viewer, faction.get(), hero)).isPresent()
                || pickedUnit.filter(unit -> game.reinforcementUnits().roster().affordable(viewer.getUniqueId(), faction.get(), unit)).isPresent();
        boolean unavailable = !affordablePick && (picked.isPresent() || pickedUnit.isPresent());
        long wait = game.deployment().waitTicks(viewer);
        var line = confirmed
                ? messages.get("deploy.status-queued", Formatter.number("seconds", (wait + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)) : wait > 0
                ? messages.get("deploy.status-waiting", Formatter.number("seconds", (wait + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND))
                : messages.get("deploy.status-ready", Formatter.number("seconds",
                        (game.deployment().autoTicks(viewer) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND));
        var target = game.deployment().suggested(viewer);
        var role = picked.map(game.heroes()::name).or(() -> pickedUnit.map(game.reinforcementUnits()::name))
                .orElseGet(() -> messages.get(fighter.combatClass().messageKey(faction.get()) + ".name"));
        var summary = new ArrayList<>(List.of(role, spawnName(target)));
        if (affordablePick) {
            int cost = picked.map(ignored -> game.heroes().cost(faction.get()))
                    .orElseGet(() -> pickedUnit.map(game.reinforcementUnits()::cost).orElse(0));
            summary.add(messages.get("deploy.cost", Formatter.number("cost", cost)));
        }
        if (unavailable) summary.add(messages.get("deploy.paid-unavailable"));
        if (confirmed || !unavailable) summary.add(messages.get(confirmed ? "deploy.queued" : affordablePick ? "deploy.confirm-paid" : "deploy.quick-lore"));
        if (!confirmed) summary.add(messages.get("deploy.auto-free"));
        if (unavailable || confirmed && affordablePick) summary.add(messages.get("deploy.paid-fallback"));
        var shownStatus = MenuIcon.DEPLOY.item().name(messages.get("deploy.status-target", Placeholder.component("status", line),
                Placeholder.component("target", targetName(target)))).lore(summary).build();
        if (!shownStatus.equals(status)) {
            status = shownStatus;
            toolbar(ToolbarButton.MIDDLE, shownStatus, game.deployment()::quickDeploy);
        }
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            var combatClass = classes[index];
            boolean selected = picked.isEmpty() && pickedUnit.isEmpty() && fighter.combatClass() == combatClass;
            set(CLASS_CARDS.get(index), game.loadouts().card(faction.get(), combatClass, selected,
                    List.of(messages.get(selected ? "class.selected" : "class.select"))));
        }
        var unit = Reinforcement.of(faction.get());
        set(SPECIALS.getFirst(), game.reinforcementUnits().card(viewer, faction.get(), unit, pickedUnit.filter(unit::equals).isPresent(), confirmed));
        var heroes = Hero.of(faction.get());
        for (int index = 0; index < heroes.size() && index + 1 < SPECIALS.size(); index++) {
            var hero = heroes.get(index);
            set(SPECIALS.get(index + 1), game.heroes().card(viewer, faction.get(), hero, picked.filter(hero::equals).isPresent(), confirmed));
        }
        refreshMap(faction.get(), chosen);
    }

    /**
     * What the screen shows that changes only on the player's own choices or seldom: who they deploy as, which cards
     * they could pay for, their battle points and which spawns there are.
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
            cards.add(new DeployScreen.ClassCard(plain(combatClass.messageKey(side) + ".short"), portrait(side, combatClass),
                    combatClass.abilities(side).stream().map(ability -> ability.icon()).toList(),
                    picked.isEmpty() && pickedUnit.isEmpty() && fighter.combatClass() == combatClass));
        }
        var unit = Reinforcement.of(side);
        var specials = new ArrayList<>(List.of(new DeployScreen.Special(plain("reinforcement." + unit.key() + ".short"),
                DeployScreen.Portrait.valueOf(unit.name()), game.reinforcementUnits().cost(unit),
                game.reinforcementUnits().roster().affordable(viewer.getUniqueId(), side, unit), pickedUnit.filter(unit::equals).isPresent())));
        for (var hero : Hero.of(side)) {
            specials.add(new DeployScreen.Special(plain("hero." + hero.key() + ".short"), DeployScreen.Portrait.valueOf(hero.name()),
                    game.heroes().cost(side), game.heroes().affordable(viewer, side, hero), picked.filter(hero::equals).isPresent()));
        }
        var places = new ArrayList<DeployScreen.Place>(List.of(new DeployScreen.Place.Headquarters(plain("deploy.headquarters"))));
        for (int index : game.posts().indices()) {
            places.add(new DeployScreen.Place.Post(index, plain("deploy.screen.post", game.posts().name(index))));
        }
        game.front().ifPresent(front -> places.add(new DeployScreen.Place.Landing(plain("deploy.screen.landing",
                Placeholder.unparsed("letter", Posts.letter(front.post()))))));
        return Optional.of(new DeployScreen.View(side, game.messages().get("deploy.title"), role, cards, specials, points, places));
    }

    private DeployScreen.Role classRole(Faction faction, CombatClass combatClass, int points) {
        var body = game.config().stats(faction, combatClass);
        var blaster = combatClass.blaster(faction);
        var stats = new ArrayList<>(List.of(hearts("health", body.health()), percent("speed", body.speed())));
        stats.addAll(blasterStats(blaster));
        return new DeployScreen.Role(plain("deploy.screen.kind.class"), plain(combatClass.messageKey(faction) + ".name"),
                portrait(faction, combatClass), stats, plain("blaster." + blaster.key() + ".name"),
                abilities(combatClass.abilities(faction).stream().map(ability -> new Named(ability.messageKey(faction), ability.icon())).toList()),
                price(Optional.empty(), points));
    }

    private DeployScreen.Role unitRole(Faction faction, Reinforcement unit, int points) {
        var body = game.reinforcementUnits().stats(unit);
        var stats = new ArrayList<>(List.of(hearts("health", body.health()), percent("speed", body.speed())));
        stats.addAll(blasterStats(unit.blaster()));
        return new DeployScreen.Role(plain("deploy.screen.kind.reinforcement"), plain("reinforcement." + unit.key() + ".name"),
                DeployScreen.Portrait.valueOf(unit.name()), stats, plain("blaster." + unit.blaster().key() + ".name"),
                abilities(unit.abilities().stream().map(ability -> new Named(ability.messageKey(faction), ability.icon())).toList()),
                price(Optional.of(body.cost()), points));
    }

    private DeployScreen.Role heroRole(Faction faction, Hero hero, int points) {
        var stats = new ArrayList<>(List.of(hearts("health", game.heroes().config().health(hero))));
        String weapon = switch (hero.weapon()) {
            case Hero.Weapon.Gun gun -> {
                stats.addAll(blasterStats(gun.blaster()));
                yield plain("blaster." + gun.blaster().key() + ".name");
            }
            case Hero.Weapon.Saber ignored -> {
                stats.add(hearts("damage", game.heroes().config().saber().damage()));
                yield plain("hero." + hero.key() + ".weapon");
            }
            case Hero.Weapon.RelicBlade ignored -> plain("relic.name");
        };
        return new DeployScreen.Role(plain("deploy.screen.kind.hero"), plain("hero." + hero.key() + ".name"),
                DeployScreen.Portrait.valueOf(hero.name()), stats, weapon,
                abilities(hero.abilities().stream().map(ability -> new Named("hero.ability." + ability.key(), ability.icon())).toList()),
                price(Optional.of(game.heroes().cost(faction)), points));
    }

    private record Named(String key, me.blockcraft.sternenfront.game.AbilityIcon icon) { }

    private List<DeployScreen.Ability> abilities(List<Named> named) {
        var keys = List.of("F", "Q");
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

    private DeployScreen.Stat percent(String stat, double share) {
        return new DeployScreen.Stat(plain("deploy.screen.stat." + stat), plain("deploy.screen.value.percent",
                Formatter.number("value", Math.round(share * 100))), false);
    }

    private List<DeployScreen.Stat> price(Optional<Integer> cost, int points) {
        return List.of(new DeployScreen.Stat(plain("deploy.screen.cost"), cost.map(value -> plain("deploy.screen.points-value",
                        Formatter.number("points", value))).orElseGet(() -> plain("deploy.screen.free")), false),
                new DeployScreen.Stat(plain("deploy.screen.balance"), plain("deploy.screen.points-value", Formatter.number("points", points)), false));
    }

    private static DeployScreen.Portrait portrait(Faction faction, CombatClass combatClass) {
        return DeployScreen.Portrait.valueOf(faction.name() + "_" + combatClass.name());
    }

    private String plain(String key, TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(game.messages().get(key, resolvers));
    }

    /**
     * A spawn's marker. The pack frames the chosen spawn, gold while it waits for a confirmation and green once
     * confirmed, marks the recommended spawn with a star and a locked one with a padlock. The spawn the player will
     * land at counts down the seconds to it.
     */
    private ItemStack marker(Faction faction, Spawn spawn, Optional<Spawn> chosen, Material icon, Component name) {
        var messages = game.messages();
        var deployment = game.deployment();
        boolean available = deployment.available(faction, spawn);
        boolean selected = chosen.filter(spawn::equals).isPresent();
        boolean confirmed = selected && game.fighter(viewer).choosing().filter(Fighter.Choosing::confirmed).isPresent();
        boolean recommended = spawn.equals(deployment.recommended(viewer));
        var lore = new ArrayList<Component>();
        if (!available) {
            lore.add(messages.get("deploy.locked"));
            lore.addAll(lockReasons(faction, spawn));
        }
        if (spawn instanceof Spawn.Post post) {
            var owner = game.posts().owner(post.index());
            var lines = new ArrayList<>(List.of(messages.get(owner.isEmpty() ? "deploy.post-neutral"
                    : owner.filter(faction::equals).isPresent() ? "deploy.post-own" : "deploy.post-enemy")));
            if (game.posts().contested(post.index())) lines.add(messages.get("deploy.post-contested"));
            lines.stream().filter(line -> !lore.contains(line)).forEach(lore::add);
        }
        if (recommended) lore.add(messages.get("deploy.recommended"));
        if (available) lore.add(messages.get(!selected ? "deploy.choose" : confirmed ? "deploy.chosen" : "deploy.pending"));
        var item = Items.of(icon).name(name).lore(lore).hideAttributes()
                .state(!available ? MenuState.LOCKED : confirmed ? MenuState.ACTIVE : selected ? MenuState.PENDING : MenuState.AVAILABLE);
        if (confirmed && available) {
            item.glow();
        }
        var marker = item.build();
        long landing = confirmed ? deployment.waitTicks(viewer) : deployment.autoTicks(viewer);
        if (available && spawn.equals(deployment.suggested(viewer)) && landing > 0) {
            marker.setData(DataComponentTypes.MAX_STACK_SIZE, MAX_COUNTDOWN);
            marker.setAmount((int) Math.min(MAX_COUNTDOWN, (landing + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND));
        }
        var look = CustomModelData.customModelData()
                .addString(available && selected ? confirmed ? "chosen" : "pending" : "")
                .addString(!available ? "locked" : recommended ? "recommended" : "");
        switch (spawn) {
            case Spawn.Post post when post.index() < 5 -> {
                var owner = game.posts().owner(post.index());
                boolean contested = game.posts().contested(post.index()) || owner.filter(faction::equals).isPresent() && !available;
                boolean filled = contested ? game.tick() / TICKS_PER_SECOND % 2 == 0 : available;
                Items.of(marker).model(Key.key("blockcraft", "sternenfront/post_" + (char) ('a' + post.index())
                        + (filled ? "_filled" : "_hollow")));
                look.addColor(owner.map(side -> Color.fromRGB(side.text().value())).orElse(Color.WHITE));
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

    /** Why the faction cannot deploy at {@code spawn} now; its headquarters is always open. */
    private List<Component> lockReasons(Faction faction, Spawn spawn) {
        var messages = game.messages();
        return switch (spawn) {
            case Spawn.Headquarters ignored -> List.of();
            case Spawn.Post post -> {
                var owner = game.posts().owner(post.index());
                yield List.of(messages.get(owner.isEmpty() ? "deploy.post-neutral"
                        : owner.get() != faction ? "deploy.post-enemy" : "deploy.post-contested"));
            }
            case Spawn.Dropship ignored -> List.of(messages.get("deploy.dropship-locked"));
        };
    }

    private Component targetName(Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> game.messages().get("deploy.target-headquarters");
            case Spawn.Post post -> game.messages().get("deploy.target-post", game.posts().name(post.index()));
            case Spawn.Dropship ignored -> game.front().map(front -> game.messages().get("deploy.target-dropship", game.posts().name(front.post())))
                    .orElseGet(() -> game.messages().get("deploy.landing"));
        };
    }

    private Component spawnName(Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> game.messages().get("deploy.headquarters");
            case Spawn.Post post -> game.messages().get("deploy.post", game.posts().name(post.index()));
            case Spawn.Dropship ignored -> game.front().map(front -> game.messages().get("deploy.dropship", game.posts().name(front.post())))
                    .orElseGet(() -> game.messages().get("deploy.landing"));
        };
    }

    /**
     * Posts and headquarters where the map puts them, north up and east right; without the pack their blocks say what
     * the badges say: a banner for a spawn, glass for a locked one, concrete for a contested one.
     */
    private void refreshMap(Faction faction, Optional<Spawn> chosen) {
        if (mapSlots.isEmpty()) {
            var markers = new ArrayList<TacticalLayout.Marker>();
            var headquarters = game.map().headquarters().get(faction).zone().getCenter();
            markers.add(new TacticalLayout.Marker(new Spawn.Headquarters(), new BattlePlan.Point(headquarters.getX(), headquarters.getZ())));
            for (int index : game.posts().indices()) {
                var center = game.map().posts().get(index).center();
                markers.add(new TacticalLayout.Marker(new Spawn.Post(index), new BattlePlan.Point(center.getX(), center.getZ())));
            }
            mapSlots = TacticalLayout.project(markers, MAP);
        }
        mapSlots.forEach((spawn, slot) -> {
            var owner = spawn instanceof Spawn.Post post ? game.posts().owner(post.index()) : Optional.of(faction);
            boolean available = game.deployment().available(faction, spawn);
            boolean contested = spawn instanceof Spawn.Post post && (game.posts().contested(post.index())
                    || owner.filter(faction::equals).isPresent() && !available);
            var shape = contested ? "_CONCRETE" : available ? "_BANNER" : "_STAINED_GLASS_PANE";
            var banner = Material.valueOf(owner.map(Faction::blocks).orElse(Faction.NEUTRAL).name() + shape);
            set(slot, marker(faction, spawn, chosen, banner, spawnName(spawn)));
        });
        set(LANDING_SLOT, game.front().map(front -> marker(faction, new Spawn.Dropship(), chosen, Material.FIREWORK_ROCKET,
                game.messages().get("deploy.dropship", game.posts().name(front.post())))).orElse(null));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
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
        mapSlots.entrySet().stream().filter(entry -> entry.getValue() == slot).findFirst()
                .ifPresent(entry -> game.deployment().choose(player, entry.getKey()));
        if (slot == LANDING_SLOT) {
            game.deployment().choose(player, new Spawn.Dropship());
        }
    }
}
