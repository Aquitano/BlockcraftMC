package me.blockcraft.system.alt;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class AddressHasherTest {

    @Test
    public void ipv6ClientsMatchByTheirSlash64Prefix() throws UnknownHostException {
        var hasher = new AddressHasher("secret");
        var rotated = hasher.hash(InetAddress.getByName("2001:db8:1:2::1"));
        assertArrayEquals(rotated, hasher.hash(InetAddress.getByName("2001:db8:1:2:abcd::9")));
        assertFalse(Arrays.equals(rotated, hasher.hash(InetAddress.getByName("2001:db8:1:3::1"))));
    }

    @Test
    public void theSameAddressHashesDifferentlyUnderAnotherSecret() throws UnknownHostException {
        var address = InetAddress.getByName("203.0.113.7");
        assertArrayEquals(new AddressHasher("secret").hash(address), new AddressHasher("secret").hash(address));
        assertFalse(Arrays.equals(new AddressHasher("secret").hash(address), new AddressHasher("other").hash(address)));
    }
}
