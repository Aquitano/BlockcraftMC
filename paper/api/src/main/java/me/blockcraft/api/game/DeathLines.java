package me.blockcraft.api.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Locale;
import java.util.Optional;

/** The line a round announces a death with: what killed the player, and who, when a recent hit credits someone. */
public final class DeathLines {

    public record Death(Optional<Player> killer, Component line) { }

    private final Messages messages;

    DeathLines(Messages messages) {
        this.messages = messages;
    }

    Death resolve(PlayerDeathEvent event, Optional<RoundTracking.Hit> hit) {
        var victim = event.getPlayer();
        var killer = hit.map(RoundTracking.Hit::attacker).map(Bukkit::getPlayer);
        String key = switch (event.getDamageSource().getDamageType().getKey().getKey()) {
            case "fall" -> "fall";
            case "out_of_world" -> "void";
            case "lava" -> "lava";
            case "in_fire", "on_fire", "hot_floor" -> "fire";
            case "drown" -> "drowning";
            case "explosion", "player_explosion" -> "explosion";
            case "arrow" -> killer.isPresent() ? "bow" : "generic";
            default -> killer.isPresent() ? "melee" : "generic";
        };
        var name = Placeholder.unparsed("player", victim.getName());
        var attacker = Placeholder.unparsed("killer", killer.map(Player::getName).orElse(""));
        var line = messages.pick("death-lines." + key, name, attacker,
                Formatter.number("distance", hit.map(RoundTracking.Hit::distance).orElse(0)));
        if (killer.isPresent() && !key.equals("bow") && !key.equals("melee")) {
            line = line.append(messages.get("death-lines.assisted", attacker));
        }
        if (key.equals("melee")) {
            String weapon = "death-lines.weapons." + hit.orElseThrow().weapon().name().toLowerCase(Locale.ROOT);
            if (messages.has(weapon)) {
                line = line.append(messages.get("death-lines.weapon", Placeholder.component("weapon", messages.get(weapon))));
            }
        }
        return new Death(killer, line);
    }
}
