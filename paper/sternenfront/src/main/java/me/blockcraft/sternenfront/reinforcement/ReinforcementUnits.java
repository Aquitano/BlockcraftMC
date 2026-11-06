package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.api.pack.MenuState;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Loadouts;
import me.blockcraft.sternenfront.hero.BattlePoints;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Optional;

public final class ReinforcementUnits {

    private final ReinforcementConfig config;
    private final ReinforcementRoster roster;
    private final BattlePoints points;
    private final Messages messages;
    private final Loadouts loadouts;

    public ReinforcementUnits(ReinforcementConfig config, BattlePoints points, Messages messages, Loadouts loadouts) {
        this.config = config;
        this.roster = new ReinforcementRoster(config, points);
        this.points = points;
        this.messages = messages;
        this.loadouts = loadouts;
    }

    public ReinforcementRoster roster() {
        return roster;
    }

    public Component name(Reinforcement unit) {
        return messages.get("reinforcement." + unit.key() + ".name");
    }

    public void equip(Player player, Reinforcement unit) {
        player.getInventory().clear();
        player.getInventory().setItem(0, loadouts.blaster(unit.blaster()));
        loadouts.dress(player, unit.faction(), Optional.of(unit.helmet()));
        player.getInventory().setHelmet(unit.helmet().item().name(name(unit)).build());
        var stats = config.stats(unit);
        Loadouts.fit(player, stats.speed(), 1);
        loadouts.health(player, stats.health());
        unit.abilities().forEach(ability -> player.setCooldown(ability.material(), 0));
    }

    public ItemStack card(Player viewer, Faction faction, Reinforcement unit, boolean picked) {
        var stats = config.stats(unit);
        var lore = new ArrayList<>(messages.getLines("reinforcement." + unit.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get("class.health", Formatter.number("hearts", stats.health() / 2)));
        lore.add(messages.get("class.speed", Formatter.number("percent", Math.round(stats.speed() * 100))));
        unit.abilities().forEach(ability -> lore.add(messages.get("class.ability",
                Placeholder.component("ability", messages.get(ability.messageKey(faction) + ".name")))));
        lore.add(messages.get("reinforcement.slots", Formatter.number("active", roster.count(unit)), Formatter.number("limit", stats.limit())));
        lore.add(messages.get("reinforcement.cost", Formatter.number("cost", stats.cost()),
                Formatter.number("points", points.of(viewer.getUniqueId()))));
        boolean affordable = roster.affordable(viewer.getUniqueId(), faction, unit);
        lore.add(messages.get(!roster.open(faction, unit) ? "reinforcement.taken" : !affordable ? "reinforcement.too-few-points"
                : picked ? "reinforcement.picked" : "reinforcement.pick"));
        if (picked && !affordable) lore.add(messages.get("reinforcement.fallback"));
        var card = unit.blaster().model().item().name(name(unit)).lore(lore).hideAttributes()
                .state(!affordable ? MenuState.LOCKED : picked ? MenuState.ACTIVE : MenuState.AVAILABLE);
        if (picked && affordable) {
            card.glow();
        }
        return card.build();
    }
}
