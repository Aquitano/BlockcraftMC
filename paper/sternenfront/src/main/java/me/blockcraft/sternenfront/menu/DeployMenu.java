package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
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
 * What a dead player chooses while they watch from above: the class they deploy as, in the upper row, their faction's
 * heroes below it, and where, in the lower: the headquarters and every post their faction holds without an enemy in
 * it. The menu redraws every second, so a post lost meanwhile locks and the wait counts down.
 */
public final class DeployMenu extends Menu {

    private static final int ROWS = 5;
    private static final int STATUS_SLOT = 4;
    private static final List<Integer> HERO_SLOTS = List.of(21, 23);
    private static final int HEADQUARTERS_SLOT = 29;
    private static final int FIRST_POST_SLOT = 31;
    private static final long TICKS_PER_SECOND = 20;

    private final SternenfrontGame game;
    private final Player viewer;

    public DeployMenu(SternenfrontGame game, Player viewer) {
        super(ROWS, game.messages().get("deploy.title"));
        this.game = game;
        this.viewer = viewer;
        closeButton();
        refresh();
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
        var status = wait > 0
                ? messages.get("deploy.status-waiting", Formatter.number("seconds", (wait + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND))
                : messages.get("deploy.status-ready");
        info(STATUS_SLOT, MenuIcon.DEPLOY.item().name(status).lore(messages.getLines("deploy.status-lore")).build());
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            var combatClass = classes[index];
            boolean selected = picked.isEmpty() && fighter.combatClass() == combatClass;
            set(ClassMenu.SLOTS.get(index), game.loadouts().card(combatClass, selected,
                    List.of(messages.get(selected ? "class.selected" : "class.select"))));
        }
        var heroes = Hero.of(faction.get());
        for (int index = 0; index < heroes.size() && index < HERO_SLOTS.size(); index++) {
            var hero = heroes.get(index);
            set(HERO_SLOTS.get(index), game.heroes().card(viewer, faction.get(), hero, picked.filter(hero::equals).isPresent()));
        }
        set(HEADQUARTERS_SLOT, spawn(faction.get(), new Spawn.Headquarters(), chosen,
                Material.valueOf(faction.get().blocks().name() + "_BANNER"), messages.get("deploy.headquarters"), List.of()));
        var posts = game.posts();
        for (int index = 0; index < posts.size(); index++) {
            var owner = posts.owner(index);
            var banner = Material.valueOf(owner.map(Faction::blocks).orElse(Faction.NEUTRAL).name() + "_BANNER");
            var why = owner.isEmpty() ? "deploy.post-neutral"
                    : owner.get() != faction.get() ? "deploy.post-enemy" : "deploy.post-contested";
            set(FIRST_POST_SLOT + index, spawn(faction.get(), new Spawn.Post(index), chosen, banner,
                    messages.get("deploy.post", posts.name(index)), List.of(messages.get(why))));
        }
    }

    private ItemStack spawn(Faction faction, Spawn spawn, Optional<Spawn> chosen, Material banner, Component name,
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
        var item = Items.of(banner).name(name).lore(lore).hideAttributes()
                .state(available ? (selected ? MenuState.ACTIVE : MenuState.AVAILABLE) : MenuState.LOCKED);
        if (selected) {
            item.glow();
        }
        return item.build();
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
        if (slot == HEADQUARTERS_SLOT) {
            game.choose(player, new Spawn.Headquarters());
        } else if (slot >= FIRST_POST_SLOT && slot < FIRST_POST_SLOT + game.posts().size()) {
            game.choose(player, new Spawn.Post(slot - FIRST_POST_SLOT));
        }
    }
}
