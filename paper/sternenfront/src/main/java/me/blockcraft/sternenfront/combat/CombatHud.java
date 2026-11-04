package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.scene.Scenes;
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
        var api = BlockcraftApi.get();
        if (api.dialogues().active(player) || api.visuals().active(player) || Scenes.isViewing(player)) return;
        var fighter = game.fighter(player);
        boolean blade = fighter.hero().filter(Hero::bladed).isPresent();
        double health = player.getHealth() / player.getAttribute(Attribute.MAX_HEALTH).getValue();
        var meter = game.messages().get(blade ? guard.broken(game.tick()) ? "combat.broken" : guard.raised()
                        ? "combat.guarding" : "combat.guard" : fighter.heat().venting(game.tick()) ? "combat.venting" : "combat.heat");
        var line = game.messages().get("combat.hud",
                Placeholder.component("health_icon", icon(0)),
                Placeholder.component("health", bar(health)),
                Formatter.number("hp", Math.ceil(player.getHealth())),
                Placeholder.component("meter_icon", icon(blade ? 2 : 1)),
                Placeholder.component("meter", bar(blade ? guard.level() : fighter.heat().level())),
                Placeholder.component("state", meter),
                Placeholder.component("first", ability(player, 0)),
                Placeholder.component("second", ability(player, 1)));
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

    private Component ability(Player player, int index) {
        var fighter = game.fighter(player);
        if (fighter.hero().filter(Hero.CHAMPION::equals).isPresent()) {
            return index == 0 ? game.messages().get("combat.champion-leap") : game.messages().get("combat.none");
        }
        var hero = fighter.hero();
        var faction = game.factionOf(player);
        var classAbility = faction.flatMap(side -> fighter.combatClass().abilities(side).stream().skip(index).findFirst());
        var key = hero.isPresent() ? hero.get().abilities().stream().skip(index).findFirst().map(ability -> "hero.ability." + ability.key())
                : classAbility.flatMap(ability -> faction.map(ability::messageKey));
        if (key.isEmpty()) {
            return game.messages().get("combat.none");
        }
        var material = hero.isPresent() ? hero.get().abilities().get(index).material() : classAbility.orElseThrow().material();
        int cooldown = player.getCooldown(material);
        var status = cooldown == 0 ? game.messages().get("combat.ready")
                : game.messages().get("combat.cooldown", Formatter.number("seconds", (cooldown + 19) / 20));
        return game.messages().get("combat.ability", Placeholder.component("key", icon(index + 3)),
                Placeholder.component("name", game.messages().get(key.get() + ".name")), Placeholder.component("status", status));
    }

    private static Component icon(int index) {
        return UiFont.glyph((char) ('\uE220' + index));
    }

    private static Component bar(double level) {
        return UiFont.glyph((char) ('\uE240' + (int) Math.round(Math.clamp(level, 0, 1) * 10)));
    }
}
