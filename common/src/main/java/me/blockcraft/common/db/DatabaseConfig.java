package me.blockcraft.common.db;

import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;

/** The {@code database} section of a config.yml; {@code BLOCKCRAFT_DB_*} environment variables override it. */
public record DatabaseConfig(String host, int port, String database, String username, String password,
                             int poolSize, Duration connectionTimeout) {

    public static DatabaseConfig fromYaml(StrictConfig section) {
        return new DatabaseConfig(
                env("HOST", section.text("host")),
                Integer.parseInt(env("PORT", section.text("port"))),
                env("NAME", section.text("database")),
                env("USER", section.text("username")),
                env("PASSWORD", section.text("password")),
                section.integer("pool-size"),
                Duration.ofSeconds(section.integer("connection-timeout-seconds"))
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
