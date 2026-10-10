package com.example.capeswitcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Comparator;

/** Thin wrapper around Mojang's official profile API (the same one the launcher uses). */
public final class CapeApi {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";
    private static final String ACTIVE = PROFILE + "/capes/active";

    public record Cape(String id, String alias, String url, boolean active) {}

    public record Profile(List<Cape> capes, String skinUrl, boolean slim) {}

    private CapeApi() {}

    public static CompletableFuture<Profile> fetchProfile(String token) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(PROFILE))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() != 200) {
                throw new RuntimeException("Profile request failed (HTTP " + res.statusCode() + ")");
            }
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();

            List<Cape> out = new ArrayList<>();
            JsonArray capes = root.has("capes") ? root.getAsJsonArray("capes") : new JsonArray();
            for (JsonElement e : capes) {
                JsonObject c = e.getAsJsonObject();
                String alias = c.has("alias") ? c.get("alias").getAsString() : c.get("id").getAsString();
                String url = c.has("url") ? c.get("url").getAsString() : null;
                boolean active = c.has("state") && "ACTIVE".equalsIgnoreCase(c.get("state").getAsString());
                out.add(new Cape(c.get("id").getAsString(), alias, url, active));
            }
            out.sort(Comparator.comparing(Cape::alias, String.CASE_INSENSITIVE_ORDER));

            String skinUrl = null;
            boolean slim = false;
            JsonArray skins = root.has("skins") ? root.getAsJsonArray("skins") : new JsonArray();
            for (JsonElement e : skins) {
                JsonObject s = e.getAsJsonObject();
                boolean active = s.has("state") && "ACTIVE".equalsIgnoreCase(s.get("state").getAsString());
                if (skinUrl == null || active) {
                    skinUrl = s.has("url") ? s.get("url").getAsString() : null;
                    slim = s.has("variant") && "SLIM".equalsIgnoreCase(s.get("variant").getAsString());
                }
            }
            return new Profile(out, skinUrl, slim);
        });
    }

    /** capeId == null hides your cape. */
    public static CompletableFuture<Void> setActive(String token, String capeId) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(ACTIVE))
                .header("Authorization", "Bearer " + token);
        if (capeId == null) {
            b.DELETE();
        } else {
            JsonObject body = new JsonObject();
            body.addProperty("capeId", capeId);
            b.header("Content-Type", "application/json")
             .PUT(HttpRequest.BodyPublishers.ofString(body.toString()));
        }
        return HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() / 100 != 2) {
                throw new RuntimeException("Cape change failed (HTTP " + res.statusCode() + ")");
            }
        });
    }

    /** Downloads a skin/cape PNG from Mojang's texture server. */
    public static CompletableFuture<byte[]> downloadBytes(String url) {
        if (url == null) {
            return CompletableFuture.failedFuture(new RuntimeException("No texture URL"));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url.replaceFirst("^http://", "https://")))
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApply(res -> {
            if (res.statusCode() != 200) {
                throw new RuntimeException("Texture download failed (HTTP " + res.statusCode() + ")");
            }
            return res.body();
        });
    }
}