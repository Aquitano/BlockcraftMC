package me.blockcraft.common.db;

import java.time.Duration;
import java.util.Map;

/** The {@code database} section of a config.yml; {@code BLOCKCRAFT_DB_*} environment variables override it. */
public record DatabaseConfig(String host, int port, String database, String username, String password,
                             int poolSize, Duration connectionTimeout) {

    public static DatabaseConfig fromYaml(Map<String, Object> section) {
        return new DatabaseConfig(
                env("HOST", String.valueOf(section.getOrDefault("host", "localhost"))),
                Integer.parseInt(env("PORT", String.valueOf(section.getOrDefault("port", 3306)))),
                env("NAME", String.valueOf(section.getOrDefault("database", "blockcraft"))),
                env("USER", String.valueOf(section.getOrDefault("username", "root"))),
                env("PASSWORD", String.valueOf(section.getOrDefault("password", ""))),
                section.get("pool-size") instanceof Number size ? size.intValue() : 4,
                Duration.ofSeconds(section.get("connection-timeout-seconds") instanceof Number seconds ? seconds.longValue() : 30)
        );
    }

    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database;
    }

    @Override
    public String toString() {
        return "DatabaseConfig[" + username + "@" + host + ":" + port + "/" + database
                + ", poolSize=" + poolSize + ", connectionTimeout=" + connectionTimeout + ", password=<redacted>]";
    }

    private static String env(String key, String fallback) {
        String value = System.getenv("BLOCKCRAFT_DB_" + key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
