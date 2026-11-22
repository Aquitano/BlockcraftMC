package me.blockcraft.zombies.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.arena.StationKind;
import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.Survivor;
import me.blockcraft.zombies.game.Weapon;
import me.blockcraft.zombies.game.ZombiesGame;
import me.blockcraft.zombies.menu.StationScreen.Art;
import me.blockcraft.zombies.menu.StationScreen.Layout;
import me.blockcraft.zombies.menu.StationScreen.Tone;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * A station's offers. With the pack each is a card of its own on the station's screen, its whole card the click; a
 * station with more offers than the screen has cards, or a player without the pack, gets a chest of one row instead,
 * its balance on the left and close on the right, the armory's guns in a row above that.
 */
public final class StationMenu extends Menu {

    private static final int COLUMNS = 9;

    /** Why an offer cannot be bought now, each with the words its card's band shows. */
    private enum Blocked {
        OWNED, ACTIVE, FULL, NO_GUN, UPGRADED, MAXED, BUSY, NONE_LEFT, ON, NOBODY_DOWN;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** What a card shows beyond the chest's item: its picture and its words in the label's capitals. */
    private record Face(Art art, String name, String line) { }

    private record Offer(Items.Builder icon, Component name, List<Component> details, int price, Supplier<Optional<Blocked>> blocked,
                         Runnable grant, Optional<Weapon> gun, Face face) {
        boolean available() {
            return blocked.get().isEmpty();
        }
    }

    private final ZombiesGame game;
    private final ArenaMap.Station station;
    private final Survivor survivor;
    private final List<Offer> offers;
    private final Optional<Layout> layout;
    private final Map<Integer, Offer> slots = new LinkedHashMap<>();

    public StationMenu(ZombiesGame game, Player player, ArenaMap.Station station) {
        var offers = offers(game, player, station);
        var layout = BlockcraftApi.get().resourcePack().applied(player) ? Layout.forOffers(offers.size()) : Optional.<Layout>empty();
        super(layout.map(cards -> cards.rows).orElse(station.kind() == StationKind.ARMORY ? 2 : 1),
                game.messages().get("shop.title", Placeholder.component("station", game.messages().get(station.nameKey()))));
        this.game = game;
        this.station = station;
        this.survivor = game.survivor(player);
        this.offers = offers;
        this.layout = layout;
        layout.ifPresentOrElse(this::cards, this::chest);
        closeButton();
    }

    private static List<Offer> offers(ZombiesGame game, Player player, ArenaMap.Station station) {
        var survivor = game.survivor(player);
        var config = game.config();
        var messages = game.messages();
        var offers = new ArrayList<Offer>();
        switch (station.kind()) {
            case ARMORY -> {
                for (var weapon : config.forSale()) {
                    var stats = config.guns().get(weapon);
                    var name = messages.get("weapon." + weapon.key());
                    offers.add(new Offer(weapon.model().item(), name, List.of(messages.get("shop.weapon-stats",
                            Formatter.number("damage", stats.damage() * stats.pellets()), Formatter.number("clip", stats.clip()))),
                            stats.price(), () -> blockedIf(survivor.weapons().containsKey(weapon), Blocked.OWNED),
                            () -> game.weapons().give(player, weapon), Optional.of(weapon),
                            new Face(Art.of(weapon), StationScreen.name(messages, weapon), StationScreen.stats(stats.damage() * stats.pellets(), stats.clip()))));
                }
                offers.add(new Offer(Items.of(Material.ARROW), messages.get("shop.ammo"), List.of(), config.ammoPrice(),
                        () -> game.weapons().held(player).map(weapon -> blockedIf(survivor.weapons().get(weapon).full(), Blocked.FULL))
                                .orElse(Optional.of(Blocked.NO_GUN)),
                        () -> game.weapons().held(player).ifPresent(weapon -> survivor.weapons().get(weapon).refill()), Optional.empty(),
                        face(game, Art.AMMO, "ammo")));
            }
            case PERKS -> {
                for (var perk : station.perk().map(List::of).orElse(List.of(Perk.values()))) {
                    var name = messages.get("perk." + perk.key());
                    var hearts = Formatter.number("hearts", config.extraHealth() / 2);
                    offers.add(new Offer(Items.of(perk.material()), name, List.of(messages.get("perk.description." + perk.key(), hearts)),
                            config.perks().get(perk), () -> blockedIf(survivor.perks().contains(perk), Blocked.ACTIVE),
                            () -> game.grantPerk(player, perk), Optional.empty(),
                            new Face(Art.of(perk), StationScreen.capitals(name), StationScreen.capitals(messages.get("perk.card." + perk.key(), hearts)))));
                }
            }
            case ARMOR -> {
                int tier = survivor.armor();
                var number = Formatter.number("tier", Math.min(tier + 1, Survivor.MAX_ARMOR));
                offers.add(new Offer(Items.of(tier == 0 ? Material.IRON_CHESTPLATE : Material.DIAMOND_CHESTPLATE),
                        messages.get("shop.armor", Formatter.number("tier", tier + 1)), List.of(), config.armorPrice() * (tier + 1),
                        () -> tier >= Survivor.MAX_ARMOR ? Optional.of(Blocked.MAXED) : blockedIf(survivor.armor() != tier, Blocked.OWNED),
                        () -> game.armor(player), Optional.empty(),
                        new Face(tier == 0 ? Art.ARMOR_IRON : Art.ARMOR_DIAMOND, StationScreen.capitals(messages.get("shop.card.armor.name", number)),
                                StationScreen.capitals(messages.get("shop.card.armor.line")))));
            }
            case POWER -> offers.add(new Offer(Items.of(Material.REDSTONE), messages.get("shop.power"), List.of(), config.powerPrice(),
                    () -> blockedIf(game.powerBought(), Blocked.ON), () -> game.powerOn(station), Optional.empty(), face(game, Art.POWER, "power")));
            case UPGRADE -> {
                var selected = game.weapons().held(player);
                var face = face(game, Art.UPGRADE, "upgrade");
                offers.add(new Offer(Items.of(Material.ENCHANTED_BOOK), messages.get("shop.upgrade"), List.of(), config.upgradePrice(),
                        () -> selected.map(weapon -> blockedIf(survivor.weapons().get(weapon).upgraded(), Blocked.UPGRADED))
                                .orElse(Optional.of(Blocked.NO_GUN)),
                        () -> survivor.weapons().get(selected.orElseThrow()).upgrade(), Optional.empty(),
                        new Face(selected.map(Art::of).orElse(Art.UPGRADE), face.name(), face.line())));
            }
            case MYSTERY -> offers.add(new Offer(Items.of(Material.ENDER_CHEST), messages.get("shop.mystery"),
                    List.of(messages.get("shop.mystery-take", Formatter.number("seconds", config.mystery().takeTicks() / 20))), config.mysteryPrice(),
                    () -> game.mysteryBox().busy(station) ? Optional.of(Blocked.BUSY) : blockedIf(!game.mysteryBox().hasGunFor(player), Blocked.NONE_LEFT),
                    () -> game.mysteryBox().roll(player, station), Optional.empty(), face(game, Art.MYSTERY, "mystery")));
            case TEAM_REVIVE -> offers.add(new Offer(Items.of(Material.TOTEM_OF_UNDYING), messages.get("shop.team-revive"), List.of(),
                    config.teamRevivePrice(), () -> blockedIf(!game.needsRevive(), Blocked.NOBODY_DOWN), game::teamRevive, Optional.empty(),
                    face(game, Art.TEAM_REVIVE, "team-revive")));
        }
        return offers;
    }

    private static Optional<Blocked> blockedIf(boolean blocked, Blocked reason) {
        return blocked ? Optional.of(reason) : Optional.empty();
    }

    private static Face face(ZombiesGame game, Art art, String key) {
        return new Face(art, StationScreen.capitals(game.messages().get("shop.card." + key + ".name")),
                StationScreen.capitals(game.messages().get("shop.card." + key + ".line")));
    }

    private void cards(Layout layout) {
        for (int index = 0; index < offers.size(); index++) {
            int slot = layout.cards.get(index);
            area(slot, StationScreen.CARD_COLUMNS, StationScreen.CARD_ROWS);
            add(slot, offers.get(index));
        }
    }

    /** Today's chest: the guns spread over the first row and ammo under them, every other station's offers in its one row. */
    private void chest() {
        int footer = station.kind() == StationKind.ARMORY ? COLUMNS : 0;
        var row = offers.stream().filter(offer -> station.kind() != StationKind.ARMORY || offer.gun().isPresent()).toList();
        for (int index = 0; index < row.size(); index++) {
            add(spread(index, row.size()), row.get(index));
        }
        if (footer > 0) {
            add(footer + COLUMNS / 2, offers.getLast());
        }
        info(footer, Items.of(Material.GOLD_INGOT).name(game.messages().get("shop.balance", Formatter.number("gold", survivor.gold()))).build());
    }

    /** The slot of offer {@code index} of {@code count}, centred in the first row with a gap between them while four or fewer. */
    private static int spread(int index, int count) {
        int step = count <= 4 ? 2 : 1;
        return COLUMNS / 2 - step * (count - 1) / 2 + step * index;
    }

    private boolean needsPower() {
        return station.kind() == StationKind.PERKS || station.kind() == StationKind.UPGRADE || station.kind() == StationKind.TEAM_REVIVE;
    }

    private void add(int slot, Offer offer) {
        slots.put(slot, offer);
        var lore = new ArrayList<>(offer.details());
        lore.add(game.messages().get("shop.price", Formatter.number("price", offer.price())));
        if (needsPower() && !game.powered()) {
            lore.add(game.messages().get(game.powerBought() ? "shop.power-out" : "shop.requires-power"));
        } else {
            lore.add(game.messages().get(offer.available() ? "shop.buy" : "shop.owned"));
        }
        if (offer.gun().isPresent() || station.kind() == StationKind.MYSTERY) {
            lore.addAll(game.messages().getLines(station.kind() == StationKind.ARMORY ? "shop.weapon-hint" : "shop.mystery-hint"));
        }
        set(slot, offer.icon().name(offer.name()).lore(lore).hideAttributes().build());
    }

    @Override
    protected Optional<Component> screen() {
        return layout.map(cards -> StationScreen.render(cards, emblem(), StationScreen.capitals(game.messages().get(station.nameKey())),
                survivor.gold(), offers.stream().map(this::card).toList()));
    }

    private Art emblem() {
        return switch (station.kind()) {
            case ARMORY -> Art.AMMO;
            case ARMOR -> Art.ARMOR_IRON;
            case PERKS -> station.perk().map(Art::of).orElse(Art.EXTRA_HEALTH);
            case POWER -> Art.POWER;
            case UPGRADE -> Art.UPGRADE;
            case MYSTERY -> Art.MYSTERY;
            case TEAM_REVIVE -> Art.TEAM_REVIVE;
        };
    }

    /** What has used an offer up shows first, then a want of power, then whether the gold reaches. */
    private StationScreen.Card card(Offer offer) {
        var face = offer.face();
        var blocked = offer.blocked().get();
        if (blocked.isPresent()) {
            return new StationScreen.Card(face.art(), face.name(), face.line(), OptionalInt.empty(), state(blocked.get().key()),
                    blocked.get() == Blocked.MAXED ? Tone.MAXED : Tone.SPENT);
        }
        var price = OptionalInt.of(offer.price());
        if (needsPower() && !game.powered()) {
            return new StationScreen.Card(face.art(), face.name(), face.line(), price, state("power"), Tone.POWER);
        }
        boolean affordable = survivor.gold() >= offer.price();
        return new StationScreen.Card(face.art(), face.name(), face.line(), price, state(affordable ? "buy" : "expensive"),
                affordable ? Tone.BUY : Tone.EXPENSIVE);
    }

    private String state(String key) {
        return StationScreen.capitals(game.messages().get("shop.state." + key));
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var offer = slots.get(slot);
        if (offer == null) {
            return;
        }
        if (offer.gun().isPresent() && offer.available() && game.weapons().full(player) && survivor.gold() >= offer.price()) {
            var gun = offer.gun().get();
            new ReplaceWeaponMenu(game, player, gun, replaced -> {
                game.purchase(player, station, offer.price(), offer.available() && survivor.weapons().containsKey(replaced),
                        () -> game.weapons().give(player, gun, replaced));
                game.openStation(player, station);
            }).returningTo(back -> game.openStation(back, station)).open(player, () -> game.canUse(player, station));
            return;
        }
        if (!game.purchase(player, station, offer.price(), offer.available() && (!needsPower() || game.powered()), offer.grant())) {
            return;
        }
        if (station.kind() == StationKind.MYSTERY) {
            Menu.close(player);
        } else {
            game.openStation(player, station);
        }
    }
}
