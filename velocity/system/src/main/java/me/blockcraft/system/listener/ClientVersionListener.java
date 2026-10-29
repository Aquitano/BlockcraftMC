package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Arrays;

public final class ClientVersionListener {

    private final ProtocolVersion minimum;
    private final Messages messages;

    public ClientVersionListener(String minecraft, Messages messages) {
        this.minimum = Arrays.stream(ProtocolVersion.values())
                .filter(version -> version.getVersionsSupportedBy().contains(minecraft))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Velocity does not support Minecraft " + minecraft));
        this.messages = messages;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (event.getResult().isAllowed() && event.getConnection().getProtocolVersion().lessThan(minimum)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(messages.get("login.version",
                    Placeholder.unparsed("version", minimum.getVersionIntroducedIn()))));
        }
    }
}
