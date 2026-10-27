package me.blockcraft.zombies.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.arena.StationKind;
import me.blockcraft.zombies.game.Perk;
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
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class StationMenu extends Menu {

    private record Offer(Component name, Material material, int price, BooleanSupplier available, Runnable grant) { }

    private final ZombiesGame game;
    private final ArenaMap.Station station;
    private final Map<Integer, Offer> offers = new LinkedHashMap<>();

    public StationMenu(ZombiesGame game, Player player, ArenaMap.Station station) {
        super(4, game.messages().get("shop.title", Placeholder.component("station", game.messages().get("station." + station.kind().key()))));
        this.game = game;
        this.station = station;
        var survivor = game.survivor(player);
        var config = game.config();
        switch (station.kind()) {
            case ARMORY -> {
                int slot = 10;
                for (var weapon : Weapon.values()) {
                    if (weapon == Weapon.PISTOL) {
                        continue;
                    }
                    add(slot, new Offer(game.messages().get("weapon." + weapon.key()), weapon.model().fallback(), config.guns().get(weapon).price(),
                            () -> !survivor.weapons().containsKey(weapon), () -> game.weapons().give(player, weapon)),
                            game.messages().get("shop.weapon-stats", Formatter.number("damage", config.guns().get(weapon).damage() * config.guns().get(weapon).pellets()),
                                    Formatter.number("clip", config.guns().get(weapon).clip())));
                    slot += 2;
                }
                add(22, new Offer(game.messages().get("shop.ammo"), Material.ARROW, config.ammoPrice(),
                        () -> game.weapons().held(player).filter(weapon -> !survivor.weapons().get(weapon).full()).isPresent(), () -> {
                            game.weapons().held(player).ifPresent(weapon -> survivor.weapons().get(weapon).refill());
                        }));
            }
            case PERKS -> {
                int slot = 10;
                for (var perk : Perk.values()) {
                    add(slot, new Offer(game.messages().get("perk." + perk.key()), perk.material(), config.perks().get(perk),
                            () -> !survivor.perks().contains(perk), () -> game.grantPerk(player, perk)),
                            game.messages().get("perk.description." + perk.key(), Formatter.number("hearts", config.extraHealth() / 2)));
                    slot += 2;
                }
            }
            case ARMOR -> {
                int tier = survivor.armor();
                add(13, new Offer(game.messages().get("shop.armor", Formatter.number("tier", tier + 1)),
                        tier == 0 ? Material.IRON_CHESTPLATE : Material.DIAMOND_CHESTPLATE,
                        config.armorPrice() * (tier + 1), () -> survivor.armor() == tier && tier < 2, () -> game.armor(player)));
            }
            case POWER -> add(13, new Offer(game.messages().get("shop.power"), Material.REDSTONE, config.powerPrice(),
                    () -> !game.powerBought(), () -> game.powerOn(station)));
            case UPGRADE -> {
                var selected = game.weapons().held(player);
                add(13, new Offer(game.messages().get("shop.upgrade"), Material.ENCHANTED_BOOK, config.upgradePrice(),
                        () -> selected.isPresent() && !survivor.weapons().get(selected.get()).upgraded(),
                        () -> survivor.weapons().get(selected.orElseThrow()).upgrade()));
            }
            case MYSTERY -> add(13, new Offer(game.messages().get("shop.mystery"), Material.ENDER_CHEST, config.mysteryPrice(),
                    () -> !game.mysteryBox().busy(station), () -> game.mysteryBox().roll(player, station)),
                    game.messages().get("shop.mystery-take", Formatter.number("seconds", config.mystery().takeTicks() / 20)));
            case TEAM_REVIVE -> add(13, new Offer(game.messages().get("shop.team-revive"), Material.TOTEM_OF_UNDYING,
                    config.teamRevivePrice(), game::needsRevive, game::teamRevive));
        }
        info(31, Items.of(Material.GOLD_INGOT).name(game.messages().get("shop.balance", Formatter.number("gold", survivor.gold()))).build());
        closeButton();
    }

    private boolean needsPower() {
        return station.kind() == StationKind.PERKS || station.kind() == StationKind.UPGRADE || station.kind() == StationKind.TEAM_REVIVE;
    }

    private void add(int slot, Offer offer, Component... details) {
        offers.put(slot, offer);
        var lore = new ArrayList<Component>();
        lore.addAll(java.util.Arrays.asList(details));
        lore.add(game.messages().get("shop.price", Formatter.number("price", offer.price())));
        if (needsPower() && !game.powered()) {
            lore.add(game.messages().get(game.powerBought() ? "shop.power-out" : "shop.requires-power"));
        } else {
            lore.add(game.messages().get(offer.available().getAsBoolean() ? "shop.buy" : "shop.owned"));
        }
        if (station.kind() == StationKind.ARMORY || station.kind() == StationKind.MYSTERY) {
            lore.addAll(game.messages().getLines("shop.weapon-hint"));
        }
        set(slot, Items.of(offer.material()).name(offer.name()).lore(lore).hideAttributes().build());
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
