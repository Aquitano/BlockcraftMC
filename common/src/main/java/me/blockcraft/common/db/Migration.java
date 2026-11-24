package me.blockcraft.common.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public record Migration(int version, List<String> statements) {

    public Migration {
        if (version < 1 || statements.isEmpty()) {
            throw new IllegalArgumentException("Invalid migration");
        }
        statements = List.copyOf(statements);
    }

    String checksum() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String sql : statements) {
                digest.update(sql.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
