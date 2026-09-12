package me.blockcraft.lobby.listener;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.storage.UnlockStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

public final class SecretsListener implements Listener {

    private static final String SIGN_MARKER = "[S]";
    private static final int NAME_LINE = 2;
    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(500));
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final Messages messages;
    private final UnlockStore found;
    private final CoinsRepository coins;
    private final int reward;

    public SecretsListener(Plugin plugin, Messages messages, UnlockStore found, CoinsRepository coins, int reward) {
        this.plugin = plugin;
        this.messages = messages;
        this.found = found;
        this.coins = coins;
        this.reward = reward;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!event.getPlayer().hasPermission(LobbyPermissions.CREATE_SECRETS) || !PLAIN.serialize(event.line(0)).equals(SIGN_MARKER)) {
            return;
        }
        Secret.byKey(PLAIN.serialize(event.line(1))).ifPresent(secret -> {
            event.line(0, messages.get("secrets.sign.border"));
            event.line(1, messages.get("secrets.sign.tag"));
            event.line(NAME_LINE, signName(secret));
            event.line(3, messages.get("secrets.sign.border"));
        });
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        String line = PLAIN.serialize(sign.getSide(Side.FRONT).line(NAME_LINE));
        secretOnSign(line).ifPresent(secret -> reveal(event.getPlayer(), secret));
    }

    private Optional<Secret> secretOnSign(String line) {
        return Arrays.stream(Secret.values())
                .filter(secret -> PLAIN.serialize(signName(secret)).equalsIgnoreCase(line))
                .findFirst();
    }

    private void reveal(Player player, Secret secret) {
        if (found.has(player.getUniqueId(), secret.key())) {
            player.sendMessage(messages.get("secrets.already-found"));
            return;
        }
        found.unlock(player.getUniqueId(), secret.key());
        var rewardResolver = Formatter.number("coins", reward);
        player.showTitle(Title.title(messages.get("secrets.found-title"), messages.get("secrets.found-subtitle", rewardResolver), TITLE_TIMES));
        messages.getLines("secrets.found", rewardResolver, Placeholder.unparsed("secret", secret.key())).forEach(player::sendMessage);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        Tasks.async(plugin, () -> {
            coins.add(player.getUniqueId(), player.getName(), reward);
            return coins.get(player.getUniqueId());
        }, balance -> {
            if (player.isOnline()) {
                player.setLevel(balance);
            }
        });
    }

    private Component signName(Secret secret) {
        return messages.get("secrets.sign.name", Placeholder.component("name", messages.get(secret.messageKey())));
    }
}
