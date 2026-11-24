package me.blockcraft.zombies.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/** After a warning, gas fills a room the squad stands in and hurts everyone who stays. */
final class ToxicFog implements WaveEvent {

    private static final Particle.DustOptions GAS = new Particle.DustOptions(Color.fromRGB(0x7A, 0xB5, 0x2E), 2.4f);
    private static final double GAS_HEIGHT = 2.5;
    private static final int BLOCKS_PER_PUFF = 12;
    private static final int WARNING_THINNING = 6;
    private final ZombiesGame game;
    private final ZombiesConfig.ToxicFog config;
    private ArenaMap.Room room;
    private int gasAt;
    private int clearsAt;

    ToxicFog(ZombiesGame game, ZombiesConfig.ToxicFog config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public Kind kind() {
        return Kind.TOXIC_FOG;
    }

    @Override
    public void start() {
        var fighters = game.fighters();
        var arena = game.arena();
        room = fighters.isEmpty() ? null
                : arena.roomAt(fighters.get(ThreadLocalRandom.current().nextInt(fighters.size())).getLocation()).orElse(null);
        if (room == null) {
            room = arena.roomAt(arena.point("spawn")).orElseThrow();
        }
        gasAt = game.tickNumber() + config.warningTicks();
        clearsAt = gasAt + config.ticks();
        game.announce("event.toxic_fog", Sounds.CORE_ALARM, roomName(), Formatter.number("seconds", config.warningTicks() / 20));
        arena.world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, 3, 0.5f);
    }

    @Override
    public boolean tick(int tick) {
        if (tick >= clearsAt) {
            game.broadcast("event.toxic_fog.cleared", roomName());
            return false;
        }
        boolean gas = tick >= gasAt;
        if (tick == gasAt) {
            game.arena().world().playSound(center(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 4, 0.5f);
        }
        if (tick % 5 == 0) {
            puff(gas ? 1 : WARNING_THINNING);
        }
        if (gas && (tick - gasAt) % config.pulseTicks() == 0) {
            for (var fighter : game.fighters()) {
                if (!room.bounds().contains(fighter.getLocation())) {
                    continue;
                }
                fighter.addPotionEffect(new PotionEffect(PotionEffectType.POISON, config.pulseTicks() + 10, 0));
                fighter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, config.slowTicks(), 0));
                game.hurt(fighter, config.damage());
            }
        }
        return true;
    }

    @Override
    public Component goal() {
        int tick = game.tickNumber();
        return tick < gasAt
                ? game.messages().get("event.toxic_fog.goal-warning", roomName(), Formatter.number("seconds", (gasAt - tick) / 20))
                : game.messages().get("event.toxic_fog.goal", roomName(), Formatter.number("seconds", Math.max(0, clearsAt - tick) / 20));
    }

    private void puff(int thinning) {
        var bounds = room.bounds();
        var world = game.arena().world();
        var random = ThreadLocalRandom.current();
        int area = (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1);
        for (int puff = 0; puff < area / BLOCKS_PER_PUFF / thinning; puff++) {
            var at = new Location(world, random.nextDouble(bounds.minX(), bounds.maxX() + 1),
                    bounds.minY() + random.nextDouble(0.2, GAS_HEIGHT), random.nextDouble(bounds.minZ(), bounds.maxZ() + 1));
            world.spawnParticle(Particle.DUST, at, 3, 1, 0.4, 1, 0, GAS);
        }
    }

    private Location center() {
        var bounds = room.bounds();
        return new Location(game.arena().world(), (bounds.minX() + bounds.maxX() + 1) / 2.0, bounds.minY() + 1,
                (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
    }

    private TagResolver roomName() {
        return Placeholder.component("room", game.messages().get("room." + room.id()));
    }
}
