package me.blockcraft.common.db;

import java.util.Map;

/** The {@code database} section of a config.yml; {@code BLOCKCRAFT_DB_*} environment variables override it. */
public record DatabaseConfig(String host, int port, String database, String username, String password) {

    public static DatabaseConfig fromYaml(Map<String, Object> section) {
        return new DatabaseConfig(
                env("HOST", String.valueOf(section.getOrDefault("host", "localhost"))),
                Integer.parseInt(env("PORT", String.valueOf(section.getOrDefault("port", 3306)))),
                env("NAME", String.valueOf(section.getOrDefault("database", "blockcraft"))),
                env("USER", String.valueOf(section.getOrDefault("username", "root"))),
                env("PASSWORD", String.valueOf(section.getOrDefault("password", "")))
        );
    }

    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv("BLOCKCRAFT_DB_" + key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
