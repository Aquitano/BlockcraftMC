package me.blockcraft.lobby;

import org.bukkit.Bukkit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.logging.Level;

public class Skin {
    final String uuid;
    String name;
    String value;
    String signatur;

    Skin(final String uuid) {
        this.uuid = uuid;
        this.load();
    }

    private void load() {
        try {
            final URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + this.uuid + "?unsigned=false");
            final URLConnection uc = url.openConnection();
            uc.setUseCaches(false);
            uc.setDefaultUseCaches(false);
            uc.addRequestProperty("User-Agent", "Mozilla/5.0");
            uc.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            uc.addRequestProperty("Pragma", "no-cache");
            final String json = new Scanner(uc.getInputStream(), "UTF-8").useDelimiter("\\A").next();
            final JSONParser parser = new JSONParser();
            final Object obj = parser.parse(json);
            final JSONArray properties = (JSONArray) ((JSONObject) obj).get("properties");
            for (Object o : properties) {
                try {
                    final JSONObject property = (JSONObject) o;
                    final String name = (String) property.get("name");
                    final String value = (String) property.get("value");
                    final String signature = property.containsKey("signature") ? ((String) property.get("signature")) : null;
                    this.name = name;
                    this.value = value;
                    this.signatur = signature;
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.WARNING, "Failed to apply auth property", e);
                }
            }
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to apply auth property", ex);
        }
    }

    public String getSkinValue() {
        return this.value;
    }

    public String getSkinName() {
        return this.name;
    }

    public String getSkinSignatur() {
        return this.signatur;
    }
}
