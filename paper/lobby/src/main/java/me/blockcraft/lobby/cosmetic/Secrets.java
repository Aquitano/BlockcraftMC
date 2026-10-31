package me.blockcraft.lobby.cosmetic;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.shop.UnlockRewards;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finding a secret, however it was found. The first find pays the reward and says how far the player has come; if
 * nobody had found that secret before, the whole network hears it, and the last secret of {@link Secret#forExplorer}
 * also earns the explorer tag. Finding one again only says so.
 */
public final class Secrets {

    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(500));

    private record Found(int balance, int found, boolean first, boolean explorer) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Unlocks unlocks;
    private final CoinsRepository coins;
    private final int reward;
    private final UnlockRewards rewards;
    private final Map<UUID, Set<Secret>> alreadyFound = new HashMap<>();
    private final Map<UUID, Set<Secret>> searching = new HashMap<>();

    public Secrets(Plugin plugin, Messages messages, Unlocks unlocks, CoinsRepository coins, int reward) {
        this.plugin = plugin;
        this.messages = messages;
        this.unlocks = unlocks;
        this.coins = coins;
        this.reward = reward;
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
    }

    /** A secret the player has found since joining is answered from memory: a sign can be clicked as fast as they like. */
    public void find(Player player, Secret secret) {
        find(player, secret, false);
    }

    /** Walking through a place finds its secret once; passing it again says nothing. */
    public void discover(Player player, Secret secret) {
        find(player, secret, true);
    }

    private void find(Player player, Secret secret, boolean quiet) {
        var api = BlockcraftApi.get();
        var uuid = player.getUniqueId();
        if (alreadyFound.getOrDefault(uuid, Set.of()).contains(secret)) {
            if (!quiet) {
                player.sendMessage(messages.get("secrets.already-found"));
            }
            return;
        }
        if (!searching.computeIfAbsent(uuid, ignored -> EnumSet.noneOf(Secret.class)).add(secret)) {
            return;
        }
        var name = api.realName(player);
        Tasks.async(plugin, () -> {
            if (!rewards.grant(uuid, name, secret.item(), reward)) {
                return Optional.<Found>empty();
            }
            var owned = unlocks.owned(uuid, Secret.ITEM_PREFIX);
            boolean explorer = Secret.forExplorer().allMatch(each -> owned.contains(each.item()))
                    && unlocks.unlock(uuid, ChatTagChoice.of(ChatTag.EXPLORER).item());
            boolean first = api.database().update("INSERT IGNORE INTO bc_secret_firsts (item, player_uuid) VALUES (?, ?)",
                    secret.item(), uuid.toString()) > 0;
            return Optional.of(new Found(coins.get(uuid), owned.size(), first, explorer));
        }, found -> {
            searching.getOrDefault(uuid, EnumSet.noneOf(Secret.class)).remove(secret);
            if (!player.isOnline()) {
                return;
            }
            alreadyFound.computeIfAbsent(uuid, ignored -> EnumSet.noneOf(Secret.class)).add(secret);
            found.ifPresentOrElse(first -> celebrate(player, secret, first), () -> {
                if (!quiet) {
                    player.sendMessage(messages.get("secrets.already-found"));
                }
            });
        }, () -> searching.getOrDefault(uuid, EnumSet.noneOf(Secret.class)).remove(secret));
    }

    public void forget(Player player) {
        alreadyFound.remove(player.getUniqueId());
        searching.remove(player.getUniqueId());
    }

    private void celebrate(Player player, Secret secret, Found found) {
        var api = BlockcraftApi.get();
        var coinsResolver = Formatter.number("coins", reward);
        var secretName = Placeholder.component("secret", messages.get(secret.messageKey()));
        api.notifications().reward(player, Title.title(messages.get("secrets.found-title"),
                messages.get("secrets.found-subtitle", coinsResolver), TITLE_TIMES), Sounds.SECRET);
        messages.getLines("secrets.found", coinsResolver, secretName,
                Formatter.number("found", found.found()), Formatter.number("total", Secret.values().length)).forEach(player::sendMessage);
        player.sendMessage(messages.get("shop.balance", Formatter.number("coins", found.balance())));
        if (found.first()) {
            ProxyMessaging.announce(plugin, player, messages.get("secrets.first-finder",
                    Placeholder.unparsed("player", player.getName()), secretName));
        }
        if (found.explorer()) {
            var tag = ChatTagChoice.of(ChatTag.EXPLORER).text(api.messages());
            messages.getLines("secrets.explorer", Placeholder.component("tag", tag)).forEach(player::sendMessage);
            Sounds.ACHIEVEMENT.play(player);
        }
    }
}
