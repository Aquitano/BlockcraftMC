package me.blockcraft.system.alt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Turns a login address into an HMAC-SHA256 under a secret that never leaves the proxy, so the stored hash cannot be
 * reversed by trying every address. Proxies sharing one table must share the secret, via {@value #SECRET_VARIABLE}.
 */
public final class AddressHasher {

    public static final String SECRET_VARIABLE = "BLOCKCRAFT_ADDRESS_SECRET";
    private static final String ALGORITHM = "HmacSHA256";
    /** An IPv6 client rotates the lower half of its address; the /64 prefix is what stays with the connection. */
    private static final int IPV6_PREFIX_BYTES = 8;

    private final SecretKeySpec key;

    AddressHasher(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public static AddressHasher load(Path dataDirectory) {
        var configured = System.getenv(SECRET_VARIABLE);
        if (configured != null && !configured.isBlank()) {
            return new AddressHasher(configured);
        }
        var file = dataDirectory.resolve("address-secret");
        try {
            if (Files.notExists(file)) {
                var random = new byte[32];
                new SecureRandom().nextBytes(random);
                Files.createDirectories(dataDirectory);
                Files.writeString(file, Base64.getEncoder().encodeToString(random));
            }
            return new AddressHasher(Files.readString(file).strip());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read or create " + file, e);
        }
    }

    public byte[] hash(InetAddress address) {
        var bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            bytes = Arrays.copyOf(bytes, IPV6_PREFIX_BYTES);
        }
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(bytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is part of every JDK", e);
        }
    }
}
