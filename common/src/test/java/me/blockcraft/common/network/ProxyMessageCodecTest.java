package me.blockcraft.common.network;

import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.settings.PlayerSetting;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ProxyMessageCodecTest {

    @Test
    public void everyMessageSurvivesTheRoundTrip() {
        var messages = List.<ProxyMessage>of(
                new ProxyMessage.Play("bedwars"),
                new ProxyMessage.Hub(),
                new ProxyMessage.Command("goto Player"),
                new ProxyMessage.NickChange(Optional.of(new Nick("Nick", "Skin", "vip"))),
                new ProxyMessage.NickChange(Optional.empty()),
                new ProxyMessage.Vanish(true),
                new ProxyMessage.Afk(true),
                new ProxyMessage.StaffNotice("blockcraft.team", "{\"text\":\"Hallo\"}"),
                new ProxyMessage.Setting(PlayerSetting.PRIVATE_MESSAGES, PlayerSetting.OFF),
                new ProxyMessage.PackRequest(),
                new ProxyMessage.FriendsRequest(UUID.randomUUID(), ProxyMessage.FriendsRequest.Action.ADD, "Thomas"),
                new ProxyMessage.FriendsReply(UUID.randomUUID(), ProxyMessage.FriendsReply.Status.OK,
                        List.of(new ProxyMessage.FriendsReply.Entry("Friend", true, "lobby-1", false)), List.of("Thomas")),
                new ProxyMessage.Pack(PackStatus.APPLIED),
                new ProxyMessage.Spectate(UUID.randomUUID()),
                new ProxyMessage.Ignore(UUID.randomUUID(), true),
                new ProxyMessage.Welcome("{\"text\":\"Regeln\"}"),
                new ProxyMessage.Announcement("{\"text\":\"Gefunden\"}"),
                new ProxyMessage.Rematch(UUID.randomUUID()),
                new ProxyMessage.Newcomer(UUID.randomUUID(), "Mia", 60));
        for (var message : messages) {
            assertEquals(Optional.of(message), ProxyMessageCodec.decode(ProxyMessageCodec.encode(message), ProxyMessage.class));
        }
    }

    @Test
    public void otherVersionsUnknownTypesAndMalformedPayloadsDecodeToEmpty() {
        var bytes = ProxyMessageCodec.encode(new ProxyMessage.Play("bedwars"));
        var otherVersion = bytes.clone();
        otherVersion[0] = 99;
        var unknownType = bytes.clone();
        unknownType[1] = 99;
        for (var payload : List.of(otherVersion, unknownType, Arrays.copyOf(bytes, bytes.length - 1),
                Arrays.copyOf(bytes, bytes.length + 1), new byte[0])) {
            assertEquals(Optional.empty(), ProxyMessageCodec.decode(payload, ProxyMessage.class));
        }
        assertEquals(Optional.empty(), ProxyMessageCodec.decode(bytes, ProxyMessage.ToBackend.class));
    }

    @Test
    public void aSettingTravelsBothWays() {
        var setting = new ProxyMessage.Setting(PlayerSetting.REPORT_NOTIFICATIONS, PlayerSetting.OFF);
        var bytes = ProxyMessageCodec.encode(setting);
        assertEquals(Optional.of(setting), ProxyMessageCodec.decode(bytes, ProxyMessage.ToProxy.class));
        assertEquals(Optional.of(setting), ProxyMessageCodec.decode(bytes, ProxyMessage.ToBackend.class));
    }
}
