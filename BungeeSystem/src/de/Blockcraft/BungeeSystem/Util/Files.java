package de.Blockcraft.BungeeSystem.Util;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class Files {
    public static Configuration BanConfig;
    public static File BanFile;
    public static Configuration MuteConfig;
    public static File MuteFile;
    public static Configuration MessagesConfiguration;
    public static File MsgFile;

    public static void setDefaultConfig() {
        if (BanConfig.get("BannedPlayers") == null) {
            BanConfig.set("BannedPlayers", null);
            try {
                ConfigurationProvider.getProvider(YamlConfiguration.class).save(BanConfig, BanFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveBanFile() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(BanConfig, BanFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveMuteFile() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(MuteConfig, MuteFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveMessagesConfiguration() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(MessagesConfiguration, MsgFile);
        } catch (Exception e1) {

        }
    }
}

