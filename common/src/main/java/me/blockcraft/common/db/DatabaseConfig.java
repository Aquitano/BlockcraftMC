package me.blockcraft.common.db;

import java.util.Map;

public record DatabaseConfig(String host, int port, String database, String username, String password) {

    public static DatabaseConfig fromYaml(Map<String, Object> section) {
        return new DatabaseConfig(
                String.valueOf(section.getOrDefault("host", "localhost")),
                ((Number) section.getOrDefault("port", 3306)).intValue(),
                String.valueOf(section.getOrDefault("database", "blockcraft")),
                String.valueOf(section.getOrDefault("username", "root")),
                String.valueOf(section.getOrDefault("password", ""))
        );
    }

    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database;
    }
}
