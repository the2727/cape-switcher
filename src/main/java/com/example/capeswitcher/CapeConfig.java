package com.example.capeswitcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CapeConfig {
    /** Show newly selected capes on this client immediately. On by default. */
    public static volatile boolean localOverride = true;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("capeswitcher.json");

    private CapeConfig() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                if (o.has("localOverride")) localOverride = o.get("localOverride").getAsBoolean();
            }
        } catch (Exception ignored) {
            // keep defaults
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("localOverride", localOverride);
            Files.writeString(FILE, o.toString());
        } catch (Exception ignored) {
            // not fatal
        }
    }
}