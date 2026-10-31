package me.blockcraft.api.cosmetic;

import me.blockcraft.common.network.operation.NetworkRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WeaponStyleTest {

    @Test
    public void everyOfferedStyleCanBeSavedThroughTheProxy() {
        for (var style : WeaponStyle.values()) {
            var request = new NetworkRequest.SetWeaponStyle(style.key());
            assertEquals(style, WeaponStyle.byKey(request.style()).orElseThrow());
        }
    }
}
