package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.arena.FrontMap;
import me.blockcraft.sternenfront.game.Faction;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 4 May, the day of the stars: every round that starts on it greets the fighters, pays its coins twice over (the
 * factor is in the config) and opens with fireworks: each faction's colour over its headquarters, both over every
 * post. They burst high and hurt nobody.
 */
final class MayTheFourth implements Listener {

    private static final double HEIGHT = 26;
    private static final long GAP_TICKS = 8;

    private record Burst(Location at, List<Faction> colours) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final FrontMap map;
    private final int factor;
    private final NamespacedKey firework;

    MayTheFourth(Plugin plugin, Messages messages, FrontMap map, int factor) {
        this.plugin = plugin;
        this.messages = messages;
        this.map = map;
        this.factor = factor;
        this.firework = new NamespacedKey(plugin, "may_the_fourth");
    }

    @EventHandler
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.phase() != GamePhase.INGAME || !BlockcraftApi.get().calendar().is(NetworkCalendar.Occasion.MAY_THE_FOURTH)) {
            return;
        }
        event.round().summary().coinFactor(factor);
        Bukkit.broadcast(messages.get("secret.may-the-fourth", Formatter.number("factor", factor)));
        var bursts = bursts();
        for (int i = 0; i < bursts.size(); i++) {
            var burst = bursts.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> launch(burst), i * GAP_TICKS);
        }
    }

    private List<Burst> bursts() {
        var bursts = new ArrayList<Burst>();
        map.headquarters().forEach((faction, hq) -> bursts.add(new Burst(above(hq.spawns().getFirst()), List.of(faction))));
        map.posts().forEach(post -> bursts.add(new Burst(above(post.center()), List.of(Faction.values()))));
        return bursts;
    }

    private static Location above(Location ground) {
        return ground.clone().add(0, HEIGHT, 0);
    }

    private void launch(Burst burst) {
        burst.at().getWorld().spawn(burst.at(), Firework.class, rocket -> {
            var meta = rocket.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(burst.colours().stream().map(Faction::bolt).toList())
                    .withFade(burst.colours().stream().map(Faction::armour).toList()).trail(true).flicker(true).build());
            meta.setPower(0);
            rocket.setFireworkMeta(meta);
            rocket.setTicksToDetonate(12);
            rocket.getPersistentDataContainer().set(firework, PersistentDataType.BOOLEAN, true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurst(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework rocket && rocket.getPersistentDataContainer().has(firework)) {
            event.setCancelled(true);
        }
    }
}
