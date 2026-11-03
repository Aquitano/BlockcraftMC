package me.blockcraft.sternenfront.game;

import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

public final class SupportRewards {

    private final SternenfrontGame game;
    private final SupportLedger ledger;
    private final int defencePoints;

    public SupportRewards(SternenfrontGame game) {
        this.game = game;
        var config = game.plugin().getConfig();
        ledger = new SupportLedger(new SupportLedger.Rules(
                Math.clamp(config.getInt("heroes.points.assist-window-seconds", 10), 1, 60) * 20,
                Math.clamp(config.getInt("heroes.points.healing-window-seconds", 20), 1, 60) * 20,
                Math.clamp(config.getInt("heroes.points.assist", 40), 0, 1000),
                Math.clamp(config.getDouble("heroes.points.healing-per-health", 2), 0, 100),
                Math.clamp(config.getInt("heroes.points.healing-limit-per-life", 40), 0, 1000)));
        defencePoints = Math.clamp(config.getInt("heroes.points.defence", 25), 0, 1000);
    }

    public void damaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !game.onField(victim)
                || !(event.getDamageSource().getCausingEntity() instanceof LivingEntity attacker)) {
            return;
        }
        var faction = game.factionOf(attacker);
        if (faction.isPresent() && game.hittable(faction.get(), victim)) {
            ledger.damage(victim.getUniqueId(), attacker instanceof Player player && game.isPlaying(player)
                            ? Optional.of(player.getUniqueId()) : Optional.empty(),
                    Math.min(victim.getHealth(), event.getFinalDamage()), game.tick());
        }
    }

    public void healed(LivingEntity patient, Optional<Player> healer, double restored) {
        var credited = healer.filter(player -> eligible(player) && game.factionOf(player).equals(game.factionOf(patient)))
                .map(Player::getUniqueId);
        int points = ledger.heal(patient.getUniqueId(), credited, restored, game.tick());
        if (points > 0) {
            healer.ifPresent(player -> game.supportPoints(player, points));
        }
    }

    public void killed(LivingEntity victim, Optional<Player> killer, int killPoints) {
        var assists = ledger.killed(victim.getUniqueId(), killer.map(Player::getUniqueId), killPoints,
                game.heroes().config().points().kill(), game.tick());
        assists.forEach((id, points) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null && eligible(player)) {
                award(player, points, "assist");
            }
        });
        killer.filter(this::eligible).filter(game::onField).ifPresent(player -> game.factionOf(player).ifPresent(faction -> {
            boolean defending = game.posts().indices().stream().anyMatch(index -> game.posts().owner(index).filter(faction::equals).isPresent()
                    && game.map().posts().get(index).contains(victim.getLocation()));
            if (defending) {
                int points = (int) Math.min(defencePoints, (long) defencePoints * killPoints
                        / Math.max(1, game.heroes().config().points().kill()));
                award(player, points, "defence");
            }
        }));
    }

    private boolean eligible(Player player) {
        return player.isOnline() && game.isPlaying(player) && game.factionOf(player).isPresent();
    }

    private void award(Player player, int points, String reason) {
        if (points > 0) {
            game.supportPoints(player, points);
            player.sendMessage(game.messages().get("support." + reason, Formatter.number("points", points)));
        }
    }

    public void removed(LivingEntity victim) {
        ledger.forgetVictim(victim.getUniqueId());
    }

    public void forget(Player player) {
        ledger.forget(player.getUniqueId());
    }

    public void clear() {
        ledger.clear();
    }
}
