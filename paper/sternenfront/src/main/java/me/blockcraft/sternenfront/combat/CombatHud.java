package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public final class CombatHud {

    private final SternenfrontGame game;

    public CombatHud(SternenfrontGame game) {
        this.game = game;
    }

    public void show(Player player, Guard guard) {
        boolean packed = BlockcraftApi.get().resourcePack().applied(player);
        var fighter = game.fighter(player);
        boolean blade = fighter.hero().filter(Hero::bladed).isPresent();
        double health = player.getHealth() / player.getAttribute(Attribute.MAX_HEALTH).getValue();
        var meter = game.messages().get(blade ? guard.broken(game.tick()) ? "combat.broken" : guard.raised()
                        ? "combat.guarding" : "combat.guard" : fighter.heat().venting(game.tick()) ? "combat.venting" : "combat.heat");
        var line = game.messages().get("combat.hud",
                Placeholder.component("health_icon", icon(packed, 0, "health")),
                Placeholder.component("health", bar(packed, health)),
                Formatter.number("hp", Math.ceil(player.getHealth())),
                Placeholder.component("meter_icon", icon(packed, blade ? 2 : 1, blade ? "guard" : "heat")),
                Placeholder.component("meter", bar(packed, blade ? guard.level() : fighter.heat().level())),
                Placeholder.component("state", meter),
                Placeholder.component("first", ability(player, 0, packed)),
                Placeholder.component("second", ability(player, 1, packed)));
        if (blade && game.combat().leapCooldown(player) > 0) {
            line = line.append(game.messages().get("combat.leap-cooldown",
                    Formatter.number("seconds", (game.combat().leapCooldown(player) + 19) / 20)));
        }
        if (game.strikeReady(player)) {
            line = line.append(game.messages().get("combat.strike-ready"));
        }
        var incoming = game.combat().damageDirection(player);
        if (incoming.isPresent()) {
            line = game.messages().get("combat.incoming." + incoming.get()).append(line);
        }
        BlockcraftApi.get().notifications().status(player, line);
    }

    private Component ability(Player player, int index, boolean packed) {
        var fighter = game.fighter(player);
        if (fighter.hero().filter(Hero.CHAMPION::equals).isPresent()) {
            return index == 0 ? game.messages().get("combat.champion-leap") : game.messages().get("combat.none");
        }
        var hero = fighter.hero();
        var key = hero.isPresent() ? hero.get().abilities().stream().skip(index).findFirst().map(ability -> "hero.ability." + ability.key())
                : fighter.combatClass().abilities().stream().skip(index).findFirst().map(ability -> "ability." + ability.key());
        if (key.isEmpty()) {
            return game.messages().get("combat.none");
        }
        var material = hero.isPresent() ? hero.get().abilities().get(index).material() : fighter.combatClass().abilities().get(index).material();
        int cooldown = player.getCooldown(material);
        var status = cooldown == 0 ? game.messages().get("combat.ready")
                : game.messages().get("combat.cooldown", Formatter.number("seconds", (cooldown + 19) / 20));
        return game.messages().get("combat.ability", Placeholder.component("key", icon(packed, index + 3, index == 0 ? "f" : "q")),
                Placeholder.component("name", game.messages().get(key.get() + ".name")), Placeholder.component("status", status));
    }

    private Component icon(boolean packed, int index, String fallback) {
        return packed ? UiFont.glyph((char) ('\uE220' + index)) : game.messages().get("combat.icons." + fallback);
    }

    private Component bar(boolean packed, double level) {
        int filled = (int) Math.round(Math.clamp(level, 0, 1) * 10);
        return packed ? UiFont.glyph((char) ('\uE240' + filled))
                : game.messages().get("combat.bar", Formatter.number("percent", filled * 10));
    }
}
