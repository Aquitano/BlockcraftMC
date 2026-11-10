package me.blockcraft.zombies.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** One enemy glows under a name tag; whoever kills it before the bounty expires earns extra gold. */
final class Bounty implements WaveEvent {

    private final ZombiesGame game;
    private final ZombiesConfig.Bounty config;
    private Mob target;
    private Component name;
    private int expiresAt;
    private boolean claimed;

    Bounty(ZombiesGame game, ZombiesConfig.Bounty config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public Kind kind() {
        return Kind.BOUNTY;
    }

    @Override
    public void start() {
        var living = game.enemies().living();
        target = living.get(ThreadLocalRandom.current().nextInt(living.size()));
        name = target.customName();
        expiresAt = game.tickNumber() + config.ticks();
        target.customName(game.messages().get("event.bounty.tag", Placeholder.component("enemy", name == null ? Component.empty() : name),
                Formatter.number("gold", config.gold())));
        target.setCustomNameVisible(true);
        target.setGlowing(true);
        game.announce("event.bounty", Sounds.STREAK, Formatter.number("gold", config.gold()),
                Formatter.number("seconds", config.ticks() / 20));
    }

    @Override
    public boolean tick(int tick) {
        if (claimed || !target.isValid()) {
            return false;
        }
        if (tick >= expiresAt) {
            game.broadcast("event.bounty.expired");
            return false;
        }
        return true;
    }

    @Override
    public void killed(Mob mob, Player killer) {
        if (claimed || !mob.equals(target)) {
            return;
        }
        claimed = true;
        if (killer == null) {
            game.broadcast("event.bounty.lost");
            return;
        }
        game.addGold(killer, config.gold());
        Sounds.COINS.playToAll();
        game.broadcast("event.bounty.claimed", Placeholder.unparsed("player", killer.getName()), Formatter.number("gold", config.gold()));
    }

    @Override
    public Component goal() {
        return game.messages().get("event.bounty.goal", Formatter.number("seconds", Math.max(0, expiresAt - game.tickNumber()) / 20),
                Formatter.number("gold", config.gold()));
    }

    @Override
    public void end() {
        if (target.isValid()) {
            target.customName(name);
            target.setCustomNameVisible(false);
            target.setGlowing(false);
        }
    }
}
