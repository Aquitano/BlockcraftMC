package me.blockcraft.zombies.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.arena.StationKind;
import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.Survivor;
import me.blockcraft.zombies.game.Weapon;
import me.blockcraft.zombies.game.ZombiesGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A station's offers in a chest of one row, its balance on the left and close on the right; the armory puts its guns
 * in a row above that.
 */
public final class StationMenu extends Menu {

    private static final int COLUMNS = 9;

    private record Offer(Component name, int price, BooleanSupplier available, Runnable grant) { }

    private final ZombiesGame game;
    private final ArenaMap.Station station;
    private final Map<Integer, Offer> offers = new LinkedHashMap<>();
    private final Map<Integer, Weapon> guns = new LinkedHashMap<>();

    public StationMenu(ZombiesGame game, Player player, ArenaMap.Station station) {
        super(station.kind() == StationKind.ARMORY ? 2 : 1,
                game.messages().get("shop.title", Placeholder.component("station", game.messages().get(station.nameKey()))));
        this.game = game;
        this.station = station;
        int footer = station.kind() == StationKind.ARMORY ? COLUMNS : 0;
        var survivor = game.survivor(player);
        var config = game.config();
        switch (station.kind()) {
            case ARMORY -> {
                var forSale = config.forSale();
                for (int index = 0; index < forSale.size(); index++) {
                    var weapon = forSale.get(index);
                    var stats = config.guns().get(weapon);
                    int slot = spread(index, forSale.size());
                    guns.put(slot, weapon);
                    add(slot, weapon.model().item(), new Offer(game.messages().get("weapon." + weapon.key()), stats.price(),
                            () -> !survivor.weapons().containsKey(weapon), () -> game.weapons().give(player, weapon)),
                            game.messages().get("shop.weapon-stats", Formatter.number("damage", stats.damage() * stats.pellets()),
                                    Formatter.number("clip", stats.clip())));
                }
                add(footer + COLUMNS / 2, Items.of(Material.ARROW), new Offer(game.messages().get("shop.ammo"), config.ammoPrice(),
                        () -> game.weapons().held(player).filter(weapon -> !survivor.weapons().get(weapon).full()).isPresent(), () -> {
                            game.weapons().held(player).ifPresent(weapon -> survivor.weapons().get(weapon).refill());
                        }));
            }
            case PERKS -> {
                var perks = station.perk().map(List::of).orElse(List.of(Perk.values()));
                for (int index = 0; index < perks.size(); index++) {
                    var perk = perks.get(index);
                    add(spread(index, perks.size()), Items.of(perk.material()), new Offer(game.messages().get("perk." + perk.key()),
                            config.perks().get(perk), () -> !survivor.perks().contains(perk), () -> game.grantPerk(player, perk)),
                            game.messages().get("perk.description." + perk.key(), Formatter.number("hearts", config.extraHealth() / 2)));
                }
            }
            case ARMOR -> {
                int tier = survivor.armor();
                add(COLUMNS / 2, Items.of(tier == 0 ? Material.IRON_CHESTPLATE : Material.DIAMOND_CHESTPLATE),
                        new Offer(game.messages().get("shop.armor", Formatter.number("tier", tier + 1)), config.armorPrice() * (tier + 1),
                                () -> survivor.armor() == tier && tier < Survivor.MAX_ARMOR, () -> game.armor(player)));
            }
            case POWER -> add(COLUMNS / 2, Items.of(Material.REDSTONE), new Offer(game.messages().get("shop.power"), config.powerPrice(),
                    () -> !game.powerBought(), () -> game.powerOn(station)));
            case UPGRADE -> {
                var selected = game.weapons().held(player);
                add(COLUMNS / 2, Items.of(Material.ENCHANTED_BOOK), new Offer(game.messages().get("shop.upgrade"), config.upgradePrice(),
                        () -> selected.isPresent() && !survivor.weapons().get(selected.get()).upgraded(),
                        () -> survivor.weapons().get(selected.orElseThrow()).upgrade()));
            }
            case MYSTERY -> add(COLUMNS / 2, Items.of(Material.ENDER_CHEST), new Offer(game.messages().get("shop.mystery"), config.mysteryPrice(),
                    () -> !game.mysteryBox().busy(station) && game.mysteryBox().hasGunFor(player),
                    () -> game.mysteryBox().roll(player, station)),
                    game.messages().get("shop.mystery-take", Formatter.number("seconds", config.mystery().takeTicks() / 20)));
            case TEAM_REVIVE -> add(COLUMNS / 2, Items.of(Material.TOTEM_OF_UNDYING), new Offer(game.messages().get("shop.team-revive"),
                    config.teamRevivePrice(), game::needsRevive, game::teamRevive));
        }
        info(footer, Items.of(Material.GOLD_INGOT).name(game.messages().get("shop.balance", Formatter.number("gold", survivor.gold()))).build());
        closeButton();
    }

    /** The slot of offer {@code index} of {@code count}, centred in the first row with a gap between them while four or fewer. */
    private static int spread(int index, int count) {
        int step = count <= 4 ? 2 : 1;
        return COLUMNS / 2 - step * (count - 1) / 2 + step * index;
    }

    private boolean needsPower() {
        return station.kind() == StationKind.PERKS || station.kind() == StationKind.UPGRADE || station.kind() == StationKind.TEAM_REVIVE;
    }

    private void add(int slot, Items.Builder icon, Offer offer, Component... details) {
        offers.put(slot, offer);
        var lore = new ArrayList<Component>();
        lore.addAll(java.util.Arrays.asList(details));
        lore.add(game.messages().get("shop.price", Formatter.number("price", offer.price())));
        if (needsPower() && !game.powered()) {
            lore.add(game.messages().get(game.powerBought() ? "shop.power-out" : "shop.requires-power"));
        } else {
            lore.add(game.messages().get(offer.available().getAsBoolean() ? "shop.buy" : "shop.owned"));
        }
        if (guns.containsKey(slot) || station.kind() == StationKind.MYSTERY) {
            lore.addAll(game.messages().getLines(station.kind() == StationKind.ARMORY ? "shop.weapon-hint" : "shop.mystery-hint"));
        }
        set(slot, icon.name(offer.name()).lore(lore).hideAttributes().build());
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var offer = offers.get(slot);
        if (offer == null) {
            return;
        }
        var gun = guns.get(slot);
        var survivor = game.survivor(player);
        if (gun != null && offer.available().getAsBoolean() && game.weapons().full(player) && survivor.gold() >= offer.price()) {
            new ReplaceWeaponMenu(game, player, gun, replaced -> {
                game.purchase(player, station, offer.price(), offer.available().getAsBoolean() && survivor.weapons().containsKey(replaced),
                        () -> game.weapons().give(player, gun, replaced));
                game.openStation(player, station);
            }).returningTo(back -> game.openStation(back, station)).open(player, () -> game.canUse(player, station));
            return;
        }
        if (!game.purchase(player, station, offer.price(), offer.available().getAsBoolean() && (!needsPower() || game.powered()), offer.grant())) {
            return;
        }
        if (station.kind() == StationKind.MYSTERY) {
            Menu.close(player);
        } else {
            game.openStation(player, station);
        }
    }
}
