package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.arena.StationKind;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/** The generator fails until a fighter holds sneak beside it, or until the wave ends. */
final class PowerOutage implements WaveEvent {

    private static final int HOLD_STEP = 5;
    private final ZombiesGame game;
    private final ArenaEffects effects;
    private final ZombiesConfig.PowerOutage config;
    private final Location generator;
    private int progress;
    private UUID fixer;
    private boolean fixed;

    PowerOutage(ZombiesGame game, ArenaEffects effects, ZombiesConfig.PowerOutage config) {
        this.game = game;
        this.effects = effects;
        this.config = config;
        this.generator = game.arena().stations().stream().filter(station -> station.kind() == StationKind.POWER)
                .findFirst().map(station -> station.location().clone()).orElseGet(() -> game.arena().point("spawn"));
    }

    @Override
    public Kind kind() {
        return Kind.POWER_OUTAGE;
    }

    @Override
    public void start() {
        effects.powerOff(generator);
        game.announce("event.power_outage", Sounds.CORE_ALARM, Formatter.number("seconds", config.fixTicks() / 20));
        game.fighters().forEach(fighter -> fighter.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, config.darknessTicks(), 0)));
    }

    @Override
    public boolean tick(int tick) {
        if (tick % HOLD_STEP != 0) {
            return true;
        }
        var center = generator.clone().add(0.5, 0, 0.5);
        var holder = game.fighters().stream().filter(fighter -> fighter.isSneaking()
                && fighter.getLocation().distanceSquared(center) <= config.radius() * config.radius()).findFirst();
        fixer = holder.map(Player::getUniqueId).orElse(null);
        progress = holder.isPresent() ? progress + HOLD_STEP : 0;
        if (tick % 20 == 0) {
            center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4);
        }
        if (holder.isEmpty()) {
            return true;
        }
        var player = holder.get();
        if (progress < config.fixTicks()) {
            BlockcraftApi.get().notifications().status(player, game.messages().get("event.power_outage.fixing",
                    Formatter.number("progress", 100 * progress / config.fixTicks())));
            return true;
        }
        fixed = true;
        game.addGold(player, config.gold());
        Sounds.PURCHASE.play(player);
        game.broadcast("event.power_outage.fixed", Placeholder.unparsed("player", player.getName()), Formatter.number("gold", config.gold()));
        return false;
    }

    @Override
    public boolean holding(Player player) {
        return player.getUniqueId().equals(fixer);
    }

    @Override
    public Component goal() {
        return game.messages().get("event.power_outage.goal", Formatter.number("progress", 100 * progress / config.fixTicks()));
    }

    @Override
    public void end() {
        if (!game.inWave()) {
            return;
        }
        effects.powerOn(generator);
        if (!fixed) {
            game.broadcast("event.power_outage.restored");
        }
    }
}
