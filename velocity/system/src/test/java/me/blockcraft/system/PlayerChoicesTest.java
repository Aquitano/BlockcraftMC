package me.blockcraft.system;

import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.settings.PlayerSetting;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerChoicesTest {

    @Test
    public void aSharedWeaponChoiceFollowsThePlayerToTheirNewBackend() {
        var proxy = new FakeProxy();
        var choices = new PlayerChoices(null, LoggerFactory.getLogger(PlayerChoicesTest.class));
        var player = proxy.join("Player");
        var previous = proxy.connect(player, "bedwars");
        choices.set(player, PlayerSetting.WEAPON_STYLE, "bone");
        var current = proxy.connect(player, "knockbackffa");

        choices.onServerConnected(new ServerPostConnectEvent(player, previous.getServer()));

        assertTrue(proxy.sentTo(previous).isEmpty());
        assertEquals(1, proxy.sentTo(current).size());
        assertEquals(new ProxyMessage.Setting(PlayerSetting.WEAPON_STYLE, "bone"),
                ProxyMessageCodec.decode(proxy.sentTo(current).getFirst(), ProxyMessage.ToBackend.class).orElseThrow());
    }
}
