package me.blockcraft.sternenfront.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.api.pack.MenuTile;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.sternenfront.game.BattleNavigation;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Fighter;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

public final class DeployMenu extends Menu {

    private static final int ROWS = 6;
    private static final List<Integer> HERO_SLOTS = List.of(40, 41);
    private static final int REINFORCEMENT_SLOT = 38;
    private static final int DROPSHIP_SLOT = 53;
    private static final long TICKS_PER_SECOND = 20;
    private static final int MAX_COUNTDOWN = 99;

    private final SternenfrontGame game;
    private final Player viewer;
    private ItemStack status;
    private final boolean tactical;
    private Map<Spawn, Integer> mapSlots = Map.of();

    public DeployMenu(SternenfrontGame game, Player viewer, boolean tactical) {
        super(ROWS, tactical ? game.messages().get("deploy.map-title")
                : UiFont.overlaid(UiFont.Overlay.STERNENFRONT_DEPLOY, game.messages().get("deploy.title")));
        this.game = game;
        this.viewer = viewer;
        this.tactical = tactical;
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.RIGHT, Items.of(tactical ? Material.CHEST : Material.MAP)
                .name(game.messages().get(tactical ? "deploy.loadout" : "deploy.map-open"))
                .lore(game.messages().getLines(tactical ? "deploy.loadout-lore" : "deploy.map-lore")).build(), player -> game.deployment().openDeploy(player, !tactical));
        if (!tactical) game.factionOf(viewer).ifPresent(faction -> {
            var classes = CombatClass.values();
            for (int index = 0; index < classes.length; index++) {
                tile(ClassMenu.SLOTS.get(index), ClassMenu.tileOf(faction, classes[index]), MenuTile.Shape.WIDE, null);
            }
        });
        refresh();
    }

    /** The posts end next to the landing's slot and the headquarters stands before them, however many the map has. */
    private int firstPostSlot() {
        return DROPSHIP_SLOT - game.posts().size();
    }

    public void refresh() {
        var messages = game.messages();
        var faction = game.factionOf(viewer);
        if (faction.isEmpty()) {
            return;
        }
        var fighter = game.fighter(viewer);
        var chosen = fighter.choosing().flatMap(Fighter.Choosing::chosen);
        var picked = fighter.choosing().flatMap(Fighter.Choosing::hero);
        var pickedUnit = fighter.choosing().flatMap(Fighter.Choosing::reinforcement);
        boolean confirmed = fighter.choosing().filter(Fighter.Choosing::confirmed).isPresent();
        boolean paid = picked.filter(hero -> game.heroes().affordable(viewer, faction.get(), hero)).isPresent()
                || pickedUnit.filter(unit -> game.reinforcementUnits().roster().affordable(viewer.getUniqueId(), faction.get(), unit)).isPresent();
        boolean unavailable = !paid && (picked.isPresent() || pickedUnit.isPresent());
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
        if (paid) {
            int cost = picked.map(ignored -> game.heroes().cost(faction.get()))
                    .orElseGet(() -> pickedUnit.map(game.reinforcementUnits()::cost).orElse(0));
            summary.add(messages.get("deploy.cost", Formatter.number("cost", cost)));
        }
        if (unavailable) summary.add(messages.get("deploy.paid-unavailable"));
        if (confirmed || !unavailable) summary.add(messages.get(confirmed ? "deploy.queued" : paid ? "deploy.confirm-paid" : "deploy.quick-lore"));
        if (!confirmed) summary.add(messages.get("deploy.auto-free"));
        if (unavailable || confirmed && paid) summary.add(messages.get("deploy.paid-fallback"));
        var shown = MenuIcon.DEPLOY.item().name(messages.get("deploy.status-target", Placeholder.component("status", line),
                Placeholder.component("target", targetName(target)))).lore(summary).build();
        if (!shown.equals(status)) {
            status = shown;
            toolbar(ToolbarButton.MIDDLE, shown, game.deployment()::quickDeploy);
        }
        if (tactical) {
            refreshMap(faction.get(), chosen);
            return;
        }
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            var combatClass = classes[index];
            boolean selected = picked.isEmpty() && pickedUnit.isEmpty() && fighter.combatClass() == combatClass;
            set(ClassMenu.SLOTS.get(index), game.loadouts().card(faction.get(), combatClass, selected,
                    List.of(messages.get(selected ? "class.selected" : "class.select"))));
        }
        var unit = Reinforcement.of(faction.get());
        set(REINFORCEMENT_SLOT, game.reinforcementUnits().card(viewer, faction.get(), unit, pickedUnit.filter(unit::equals).isPresent(), confirmed));
        var heroes = Hero.of(faction.get());
        for (int index = 0; index < heroes.size() && index < HERO_SLOTS.size(); index++) {
            var hero = heroes.get(index);
            set(HERO_SLOTS.get(index), game.heroes().card(viewer, faction.get(), hero, picked.filter(hero::equals).isPresent(), confirmed));
        }
        set(firstPostSlot() - 1, spawn(faction.get(), new Spawn.Headquarters(), chosen,
                Material.valueOf(faction.get().blocks().name() + "_BANNER"), messages.get("deploy.headquarters")));
        var posts = game.posts();
        for (int index = 0; index < posts.size(); index++) {
            var banner = Material.valueOf(posts.owner(index).map(Faction::blocks).orElse(Faction.NEUTRAL).name() + "_BANNER");
            set(firstPostSlot() + index, spawn(faction.get(), new Spawn.Post(index), chosen, banner, messages.get("deploy.post", posts.name(index))));
        }
        set(DROPSHIP_SLOT, game.front().map(front -> spawn(faction.get(), new Spawn.Dropship(), chosen, Material.FIREWORK_ROCKET,
                messages.get("deploy.dropship", posts.name(front.post())))).orElse(null));
    }

    /**
     * A spawn's marker, the same in both views. The pack frames the chosen spawn, gold while it waits for a confirmation
     * and green once confirmed, marks the recommended spawn with a star and a locked one with a padlock. The spawn the
     * player will land at counts down the seconds to it.
     */
    private ItemStack spawn(Faction faction, Spawn spawn, Optional<Spawn> chosen, Material icon, Component name) {
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

    private void refreshMap(Faction faction, Optional<Spawn> chosen) {
        if (mapSlots.isEmpty()) {
            var markers = new ArrayList<TacticalLayout.Marker>();
            var headquarters = game.map().headquarters().get(faction).zone().getCenter();
            markers.add(new TacticalLayout.Marker(new Spawn.Headquarters(), new BattleNavigation.Point(headquarters.getX(), headquarters.getZ())));
            for (int index : game.posts().indices()) {
                var center = game.map().posts().get(index).center();
                markers.add(new TacticalLayout.Marker(new Spawn.Post(index), new BattleNavigation.Point(center.getX(), center.getZ())));
            }
            mapSlots = TacticalLayout.project(markers);
        }
        mapSlots.forEach((spawn, slot) -> {
            var owner = spawn instanceof Spawn.Post post ? game.posts().owner(post.index()) : Optional.of(faction);
            boolean available = game.deployment().available(faction, spawn);
            boolean contested = spawn instanceof Spawn.Post post && (game.posts().contested(post.index())
                    || owner.filter(faction::equals).isPresent() && !available);
            var shape = contested ? "_CONCRETE" : available ? "_BANNER" : "_STAINED_GLASS_PANE";
            var banner = Material.valueOf(owner.map(Faction::blocks).orElse(Faction.NEUTRAL).name() + shape);
            set(slot, spawn(faction, spawn, chosen, banner, spawnName(spawn)));
        });
        info(49, Items.of(Material.COMPASS).name(game.messages().get("deploy.map-orientation"))
                .lore(game.messages().getLines("deploy.map-lore")).build());
        set(DROPSHIP_SLOT, game.front().map(front -> spawn(faction, new Spawn.Dropship(), chosen, Material.FIREWORK_ROCKET,
                game.messages().get("deploy.dropship", game.posts().name(front.post())))).orElse(null));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (tactical) {
            mapSlots.entrySet().stream().filter(entry -> entry.getValue() == slot).findFirst()
                    .ifPresent(entry -> game.deployment().choose(player, entry.getKey()));
            if (slot == DROPSHIP_SLOT) game.deployment().choose(player, new Spawn.Dropship());
            return;
        }
        int classIndex = ClassMenu.SLOTS.indexOf(slot);
        if (classIndex >= 0) {
            game.chooseClass(player, CombatClass.values()[classIndex]);
            return;
        }
        if (slot == REINFORCEMENT_SLOT) {
            game.factionOf(player).ifPresent(faction -> game.chooseReinforcement(player, Reinforcement.of(faction)));
            return;
        }
        int heroIndex = HERO_SLOTS.indexOf(slot);
        var heroes = game.factionOf(player).map(Hero::of).orElse(List.of());
        if (heroIndex >= 0 && heroIndex < heroes.size()) {
            game.chooseHero(player, heroes.get(heroIndex));
            return;
        }
        if (slot == firstPostSlot() - 1) {
            game.deployment().choose(player, new Spawn.Headquarters());
        } else if (slot >= firstPostSlot() && slot < DROPSHIP_SLOT) {
            game.deployment().choose(player, new Spawn.Post(slot - firstPostSlot()));
        } else if (slot == DROPSHIP_SLOT) {
            game.deployment().choose(player, new Spawn.Dropship());
        }
    }
}
