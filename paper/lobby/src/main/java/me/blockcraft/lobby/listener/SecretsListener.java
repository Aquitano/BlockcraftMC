package me.blockcraft.lobby.listener;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.cosmetic.Secret;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
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
import java.util.OptionalInt;

public final class SecretsListener implements Listener {

    private static final String SIGN_MARKER = "[S]";
    private static final int NAME_LINE = 2;
    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(500));
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final Messages messages;
    private final Unlocks unlocks;
    private final CoinsRepository coins;
    private final int reward;

    public SecretsListener(Plugin plugin, Messages messages, Unlocks unlocks, CoinsRepository coins, int reward) {
        this.plugin = plugin;
        this.messages = messages;
        this.unlocks = unlocks;
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
        var uuid = player.getUniqueId();
        var name = player.getName();
        Tasks.async(plugin, () -> {
            if (!unlocks.unlock(uuid, secret.item())) {
                return OptionalInt.empty();
            }
            coins.add(uuid, name, reward);
            return OptionalInt.of(coins.get(uuid));
        }, balance -> {
            if (!player.isOnline()) {
                return;
            }
            if (balance.isEmpty()) {
                player.sendMessage(messages.get("secrets.already-found"));
                return;
            }
            var rewardResolver = Formatter.number("coins", reward);
            player.showTitle(Title.title(messages.get("secrets.found-title"), messages.get("secrets.found-subtitle", rewardResolver), TITLE_TIMES));
            messages.getLines("secrets.found", rewardResolver, Placeholder.unparsed("secret", secret.key())).forEach(player::sendMessage);
            Sounds.SECRET.play(player);
            player.sendMessage(messages.get("shop.balance", Formatter.number("coins", balance.getAsInt())));
        });
    }

    private Component signName(Secret secret) {
        return messages.get("secrets.sign.name", Placeholder.component("name", messages.get(secret.messageKey())));
    }
}
