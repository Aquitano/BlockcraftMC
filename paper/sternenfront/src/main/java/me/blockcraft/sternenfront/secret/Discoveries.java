package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.shop.UnlockRewards;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Sabers;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hands out Tessar's secrets as the network does: the first find unlocks the secret and pays its coins in one
 * transaction, a later find only says the player knows it already. A player hears about each secret once per round.
 * Whoever holds all five kyber crystals also unlocks the white blade, and the whole server hears of it.
 */
final class Discoveries implements Listener {

    private static final int BANNER_TICKS = 56;

    private record Found(boolean first, int crystals, boolean saber) {
    }

    private final SternenfrontGame game;
    private final Messages messages;
    private final int coins;
    private final UnlockRewards rewards;
    private final Map<UUID, Set<Secret>> answered = new HashMap<>();

    Discoveries(SternenfrontGame game, Messages messages, int coins) {
        this.game = game;
        this.messages = messages;
        this.coins = coins;
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
    }

    void find(Player player, Secret secret) {
        var uuid = player.getUniqueId();
        if (!answered.computeIfAbsent(uuid, ignored -> EnumSet.noneOf(Secret.class)).add(secret)) {
            return;
        }
        var api = BlockcraftApi.get();
        var name = api.realName(player);
        api.playerTasks().write(player, () -> {
            boolean first = rewards.grant(uuid, name, secret.item(), coins);
            if (!secret.crystal()) {
                return new Found(first, 0, false);
            }
            int crystals = api.unlocks().owned(uuid, Secret.KYBER).size();
            return new Found(first, crystals, crystals >= Secret.CRYSTALS.size() && api.unlocks().unlock(uuid, Sabers.WHITE_UNLOCK));
        }, found -> tell(player, secret, found), failed -> answered.getOrDefault(uuid, Set.of()).remove(secret));
    }

    private void tell(Player player, Secret secret, Found found) {
        var secretName = Placeholder.component("secret", messages.get(secret.messageKey()));
        if (!found.first()) {
            player.sendMessage(messages.get("secret.known", secretName));
            return;
        }
        var coinsTag = Formatter.number("coins", coins);
        game.announce(player, messages.get("secret.found-banner", coinsTag), BANNER_TICKS);
        BlockcraftApi.get().notifications().rewardSound(player, Sounds.SECRET);
        player.sendMessage(messages.get("secret.found", secretName, coinsTag));
        if (secret.crystal()) {
            SoundCue.KYBER_FOUND.playTo(player, 1f);
            player.sendMessage(messages.get("secret.kyber-count", Formatter.number("found", found.crystals()),
                    Formatter.number("total", Secret.CRYSTALS.size())));
        }
        if (found.saber()) {
            Bukkit.broadcast(messages.get("secret.saber", Placeholder.unparsed("player", player.getName())));
            Bukkit.getOnlinePlayers().forEach(Sounds.ACHIEVEMENT::play);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        answered.remove(event.getPlayer().getUniqueId());
    }
}
