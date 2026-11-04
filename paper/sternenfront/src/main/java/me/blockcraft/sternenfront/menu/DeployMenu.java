package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.api.pack.MenuTile;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Fighter;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a dead player chooses while they watch from above: the role they deploy as, on the same cards as the class menu,
 * their faction's heroes below them, and where in the last row: the headquarters, every post their faction holds
 * without an enemy in it and, from the assault on, the trailing faction's landing. The status sits in the toolbar. The
 * menu redraws every second, so a post lost meanwhile locks and the wait counts down.
 */
public final class DeployMenu extends Menu {

    private static final int ROWS = 6;
    private static final List<Integer> HERO_SLOTS = List.of(40, 41);
    private static final int DROPSHIP_SLOT = 53;
    private static final long TICKS_PER_SECOND = 20;

    private final SternenfrontGame game;
    private final Player viewer;
    private ItemStack status;

    public DeployMenu(SternenfrontGame game, Player viewer) {
        super(ROWS, UiFont.overlaid(UiFont.Overlay.STERNENFRONT_DEPLOY, game.messages().get("deploy.title")));
        this.game = game;
        this.viewer = viewer;
        closeButton(ToolbarButton.LEFT);
        game.factionOf(viewer).ifPresent(faction -> {
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
        long wait = game.waitTicks(viewer);
        var line = wait > 0
                ? messages.get("deploy.status-waiting", Formatter.number("seconds", (wait + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND))
                : messages.get("deploy.status-ready");
        var shown = MenuIcon.DEPLOY.item().name(line).lore(messages.getLines("deploy.status-lore")).build();
        if (!shown.equals(status)) {
            status = shown;
            toolbar(ToolbarButton.MIDDLE, shown, ignored -> { });
        }
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            var combatClass = classes[index];
            boolean selected = picked.isEmpty() && fighter.combatClass() == combatClass;
            set(ClassMenu.SLOTS.get(index), game.loadouts().card(faction.get(), combatClass, selected,
                    List.of(messages.get(selected ? "class.selected" : "class.select"))));
        }
        var heroes = Hero.of(faction.get());
        for (int index = 0; index < heroes.size() && index < HERO_SLOTS.size(); index++) {
            var hero = heroes.get(index);
            set(HERO_SLOTS.get(index), game.heroes().card(viewer, faction.get(), hero, picked.filter(hero::equals).isPresent()));
        }
        set(firstPostSlot() - 1, spawn(faction.get(), new Spawn.Headquarters(), chosen,
                Material.valueOf(faction.get().blocks().name() + "_BANNER"), messages.get("deploy.headquarters"), List.of()));
        var posts = game.posts();
        for (int index = 0; index < posts.size(); index++) {
            var owner = posts.owner(index);
            var banner = Material.valueOf(owner.map(Faction::blocks).orElse(Faction.NEUTRAL).name() + "_BANNER");
            var why = owner.isEmpty() ? "deploy.post-neutral"
                    : owner.get() != faction.get() ? "deploy.post-enemy" : "deploy.post-contested";
            set(firstPostSlot() + index, spawn(faction.get(), new Spawn.Post(index), chosen, banner,
                    messages.get("deploy.post", posts.name(index)), List.of(messages.get(why))));
        }
        game.front().ifPresent(front -> set(DROPSHIP_SLOT, spawn(faction.get(), new Spawn.Dropship(), chosen, Material.FIREWORK_ROCKET,
                messages.get("deploy.dropship", posts.name(front.post())), List.of(messages.get("deploy.dropship-locked")))));
    }

    private ItemStack spawn(Faction faction, Spawn spawn, Optional<Spawn> chosen, Material icon, Component name,
                            List<Component> unavailable) {
        var messages = game.messages();
        boolean available = game.available(faction, spawn);
        boolean selected = chosen.filter(spawn::equals).isPresent();
        var lore = new ArrayList<Component>();
        if (!available) {
            lore.addAll(unavailable);
        } else if (selected) {
            lore.add(messages.get("deploy.chosen"));
        } else {
            lore.add(messages.get("deploy.choose"));
        }
        var item = Items.of(icon).name(name).lore(lore).hideAttributes()
                .state(available ? (selected ? MenuState.ACTIVE : MenuState.AVAILABLE) : MenuState.LOCKED);
        if (selected) {
            item.glow();
        }
        return item.build();
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return !ToolbarButton.MIDDLE.covers(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        int classIndex = ClassMenu.SLOTS.indexOf(slot);
        if (classIndex >= 0) {
            game.chooseClass(player, CombatClass.values()[classIndex]);
            return;
        }
        int heroIndex = HERO_SLOTS.indexOf(slot);
        var heroes = game.factionOf(player).map(Hero::of).orElse(List.of());
        if (heroIndex >= 0 && heroIndex < heroes.size()) {
            game.chooseHero(player, heroes.get(heroIndex));
            return;
        }
        if (slot == firstPostSlot() - 1) {
            game.choose(player, new Spawn.Headquarters());
        } else if (slot >= firstPostSlot() && slot < DROPSHIP_SLOT) {
            game.choose(player, new Spawn.Post(slot - firstPostSlot()));
        } else if (slot == DROPSHIP_SLOT) {
            game.choose(player, new Spawn.Dropship());
        }
    }
}
