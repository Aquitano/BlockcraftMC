package me.blockcraft.lobby.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Arrays;
import java.util.Optional;

/** The secrets hidden as signs in the map: staff write {@code [S]} and the key, and a player's right-click finds one. */
public final class SecretsListener implements Listener {

    private static final String SIGN_MARKER = "[S]";
    private static final int NAME_LINE = 2;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Messages messages;
    private final Secrets secrets;

    public SecretsListener(Messages messages, Secrets secrets) {
        this.messages = messages;
        this.secrets = secrets;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!event.getPlayer().hasPermission(LobbyPermissions.CREATE_SECRETS) || !PLAIN.serialize(event.line(0)).equals(SIGN_MARKER)) {
            return;
        }
        Secret.byKey(PLAIN.serialize(event.line(1))).filter(secret -> secret.trigger() == Secret.Trigger.SIGN).ifPresent(secret -> {
            event.line(0, messages.get("secrets.sign.border"));
            event.line(1, messages.get("secrets.sign.tag"));
            event.line(NAME_LINE, signName(secret));
            event.line(3, messages.get("secrets.sign.border"));
        });
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null || !Tag.ALL_SIGNS.isTagged(block.getType()) || !(block.getState() instanceof Sign sign)) {
            return;
        }
        String line = PLAIN.serialize(sign.getSide(Side.FRONT).line(NAME_LINE));
        secretOnSign(line).ifPresent(secret -> secrets.find(event.getPlayer(), secret));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        secrets.forget(event.getPlayer());
    }

    private Optional<Secret> secretOnSign(String line) {
        return Arrays.stream(Secret.values())
                .filter(secret -> secret.trigger() == Secret.Trigger.SIGN)
                .filter(secret -> PLAIN.serialize(signName(secret)).equalsIgnoreCase(line))
                .findFirst();
    }

    private Component signName(Secret secret) {
        return messages.get("secrets.sign.name", Placeholder.component("name", messages.get(secret.messageKey())));
    }
}
