package me.blockcraft.sternenfront.hero;

import me.blockcraft.api.pack.MenuState;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Loadouts;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The round's heroes: the battle points that buy them, the slots each faction has for them, what a hero carries,
 * and the boss bar everyone sees of every living hero's health. The champion of the relic is a hero here too.
 */
public final class Heroes {

    private static final int BAR_TICKS = 5;
    private static final double TO_HEARTS = 2;

    private final SternenfrontGame game;
    private final Messages messages;
    private final Loadouts loadouts;
    private final HeroConfig config;
    private final BattlePoints points = new BattlePoints();
    private final HeroRoster roster;
    private final Sabers sabers;
    private final Powers powers;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private Optional<Faction> comeback = Optional.empty();

    public Heroes(SternenfrontGame game, Plugin plugin, Messages messages, Loadouts loadouts, HeroConfig config) {
        this.game = game;
        this.messages = messages;
        this.loadouts = loadouts;
        this.config = config;
        this.roster = new HeroRoster(config.perFaction());
        this.sabers = new Sabers(plugin, messages, config.saber());
        this.powers = new Powers(game, config, sabers);
    }

    public HeroConfig config() {
        return config;
    }

    public Sabers sabers() {
        return sabers;
    }

    public Powers powers() {
        return powers;
    }

    public int points(Player player) {
        return points.of(player.getUniqueId());
    }

    public void earn(Player player, int amount) {
        points.earn(player.getUniqueId(), amount);
    }

    public Optional<Hero> of(Player player) {
        return roster.of(player.getUniqueId());
    }

    public boolean open(Faction faction, Hero hero) {
        return roster.open(faction, hero);
    }

    public boolean available(Faction faction) {
        return Arrays.stream(Hero.values()).anyMatch(hero -> open(faction, hero));
    }

    public boolean affordable(Player player, Faction faction, Hero hero) {
        return open(faction, hero) && points(player) >= cost(faction);
    }

    public int cost(Faction faction) {
        return comeback.filter(faction::equals)
                .map(ignored -> (int) Math.round(config.cost() * game.config().acts().comebackHeroShare()))
                .orElse(config.cost());
    }

    /** For the rest of the round {@code faction}'s heroes cost less and it may field one more of them. */
    public void comeback(Faction faction) {
        comeback = Optional.of(faction);
        roster.grantSlot(faction);
    }

    /** Makes the player the hero, spending their points unless the relic made them champion; false if it cannot be. */
    public boolean enlist(Player player, Faction faction, Hero hero) {
        if (hero != Hero.CHAMPION && (!affordable(player, faction, hero) || !points.spend(player.getUniqueId(), cost(faction)))) {
            return false;
        }
        return roster.enlist(player.getUniqueId(), faction, hero);
    }

    public void equip(Player player, Faction faction, Hero hero, long tick) {
        hero.abilities().forEach(ability -> player.setCooldown(ability.material(), 0));
        if (hero == Hero.CHAMPION) {
            loadouts.arm(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        } else {
            var inventory = player.getInventory();
            inventory.clear();
            switch (hero.weapon()) {
                case Hero.Weapon.Saber ignored -> sabers.draw(player, hero, tick);
                case Hero.Weapon.Gun gun -> {
                    var blaster = loadouts.blaster(gun.blaster());
                    inventory.setItem(0, blaster);
                    if (gun.blaster().dual()) {
                        inventory.setItemInOffHand(blaster.clone());
                    }
                }
                case Hero.Weapon.RelicBlade ignored -> { }
            }
            loadouts.dress(player, faction, hero.head());
            loadouts.health(player, config.health(hero));
        }
        player.getInventory().setHeldItemSlot(0);
        var bar = BossBar.bossBar(Component.empty(), 1, colour(hero, faction), BossBar.Overlay.NOTCHED_10);
        Optional.ofNullable(bars.put(player.getUniqueId(), bar)).ifPresent(this::hide);
        draw(player, hero, bar);
        Bukkit.getOnlinePlayers().forEach(viewer -> viewer.showBossBar(bar));
    }

    /** The hero as the deploy menu offers it: their weapon, what they do, and whether the viewer can be them now. */
    public ItemStack card(Player viewer, Faction faction, Hero hero, boolean picked) {
        var lore = new ArrayList<>(messages.getLines("hero." + hero.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get("hero.cost", Formatter.number("cost", cost(faction)), Formatter.number("points", points(viewer))));
        boolean affordable = affordable(viewer, faction, hero);
        lore.add(messages.get(picked ? "hero.picked" : !open(faction, hero) ? "hero.card-taken"
                : affordable ? "hero.pick" : "hero.card-too-few"));
        var model = hero.weapon().model();
        var card = model.item().name(name(hero)).lore(lore).hideAttributes()
                .state(picked ? MenuState.ACTIVE : affordable ? MenuState.AVAILABLE : MenuState.LOCKED);
        if (picked) {
            card.glow();
        }
        var item = card.build();
        if (model.tinted()) {
            Sabers.tint(item, model, sabers.colour(viewer, hero));
        }
        return item;
    }

    public Optional<Hero> discharge(Player player) {
        Optional.ofNullable(bars.remove(player.getUniqueId())).ifPresent(this::hide);
        sabers.forget(player);
        powers.forget(player);
        return roster.discharge(player.getUniqueId());
    }

    public void forget(Player player) {
        points.forget(player.getUniqueId());
    }

    public void showBars(Player viewer) {
        bars.values().forEach(viewer::showBossBar);
    }

    public void tick(long tick) {
        var sabered = new LinkedHashMap<Player, Hero>();
        roster.all().forEach((id, hero) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null && hero.weapon() instanceof Hero.Weapon.Saber && game.onField(player)) {
                sabered.put(player, hero);
            }
        });
        sabers.tick(tick, sabered);
        powers.tick(tick);
        if (tick % BAR_TICKS == 0) {
            bars.forEach((id, bar) -> {
                var player = Bukkit.getPlayer(id);
                if (player != null) {
                    roster.of(id).ifPresent(hero -> draw(player, hero, bar));
                }
            });
        }
    }

    private void draw(Player player, Hero hero, BossBar bar) {
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        double ceiling = max == null ? 20 : max.getValue();
        double health = player.isDead() ? 0 : player.getHealth();
        bar.name(messages.get("hero.bar", Placeholder.component("hero", name(hero)),
                Placeholder.unparsed("player", player.getName()), Formatter.number("hearts", Math.ceil(health) / TO_HEARTS)));
        bar.progress((float) Math.clamp(health / ceiling, 0, 1));
    }

    public Component name(Hero hero) {
        return messages.get("hero." + hero.key() + ".name");
    }

    private static BossBar.Color colour(Hero hero, Faction faction) {
        if (hero == Hero.CHAMPION) {
            return BossBar.Color.WHITE;
        }
        return faction == Faction.KOLONIEN ? BossBar.Color.BLUE : BossBar.Color.RED;
    }

    private void hide(BossBar bar) {
        Bukkit.getOnlinePlayers().forEach(viewer -> viewer.hideBossBar(bar));
    }

    public void clear() {
        bars.values().forEach(this::hide);
        bars.clear();
        roster.clear();
        comeback = Optional.empty();
        points.clear();
        sabers.clear();
        powers.clear();
    }
}
