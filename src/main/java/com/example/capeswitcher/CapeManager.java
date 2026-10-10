package com.example.capeswitcher;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Holds the cape list + textures (fetched once) and enforces the switch cooldown. */
public final class CapeManager {
    public static final long COOLDOWN_MS = 30_000;

    private static volatile List<CapeApi.Cape> capes = List.of();
    private static final Map<String, Identifier> previews = new ConcurrentHashMap<>(); // "" = no cape
    private static final Map<String, Identifier> flatCapes = new ConcurrentHashMap<>(); // raw cape textures
    private static volatile boolean slim = false;
    private static volatile boolean previewsDone = false;
    private static volatile boolean loading = false;
    private static volatile boolean loaded = false;
    private static volatile String error = null;
    private static volatile boolean switching = false;
    private static volatile boolean switched = false;
    private static volatile long lastSwitch = 0;
    /** The cape other players see: your account's cape at startup / your last server join. */
    private static volatile String serverCapeId = null;

    private CapeManager() {}

    public static List<CapeApi.Cape> capes() { return capes; }
    public static boolean isSlim() { return slim; }
    public static boolean previewsDone() { return previewsDone; }
    public static boolean isLoading() { return loading; }
    public static boolean isLoaded() { return loaded; }
    public static boolean isSwitching() { return switching; }
    public static boolean hasSwitched() { return switched; }
    public static String error() { return error; }

    public static Identifier previewTexture(String capeId) {
        return previews.get(capeId == null ? "" : capeId);
    }

    public static Identifier flatCapeTexture(String capeId) {
        return capeId == null ? null : flatCapes.get(capeId);
    }

    /** The cape currently active on your Mojang account (null = none). */
    public static String activeCapeId() {
        for (CapeApi.Cape c : capes) {
            if (c.active()) return c.id();
        }
        return null;
    }

    public static String serverCapeId() { return serverCapeId; }

    /** The cape shown on your own client. */
    public static String clientCapeId() {
        return CapeConfig.localOverride && switched ? activeCapeId() : serverCapeId;
    }

    public static String capeName(String capeId) {
        if (capeId == null) return "None";
        for (CapeApi.Cape c : capes) {
            if (c.id().equals(capeId)) return c.alias();
        }
        return capeId;
    }

    /** Called when you join a server: that server now sees your account's current cape. */
    public static void snapshotServerCape() {
        if (loaded) serverCapeId = activeCapeId();
    }

    public static int cooldownSeconds() {
        long left = COOLDOWN_MS - (System.currentTimeMillis() - lastSwitch);
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public static void fetchOnce() {
        if (loaded || loading) return;
        loading = true;
        error = null;
        String token = Minecraft.getInstance().getUser().getAccessToken();
        CapeApi.fetchProfile(token).whenComplete((profile, err) -> {
            if (err != null) {
                error = rootMessage(err);
            } else {
                capes = profile.capes();
                slim = profile.slim();
                serverCapeId = activeCapeId();
                loaded = true;
                buildTextures(profile);
            }
            loading = false;
        });
    }

    private static void buildTextures(CapeApi.Profile profile) {
        CompletableFuture<byte[]> skinF = CapeApi.downloadBytes(profile.skinUrl()).exceptionally(t -> null);
        Map<String, CompletableFuture<byte[]>> capeFs = new LinkedHashMap<>();
        for (CapeApi.Cape c : profile.capes()) {
            capeFs.put(c.id(), CapeApi.downloadBytes(c.url()).exceptionally(t -> null));
        }
        List<CompletableFuture<?>> all = new ArrayList<>();
        all.add(skinF);
        all.addAll(capeFs.values());

        CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).thenRun(() -> {
            byte[] skin = skinF.join();
            Map<String, byte[]> capeBytes = new HashMap<>();
            capeFs.forEach((id, f) -> capeBytes.put(id, f.join()));
            Minecraft.getInstance().execute(() -> compose(skin, capeBytes));
        });
    }

    /** Runs on the main thread. */
    private static void compose(byte[] skin, Map<String, byte[]> capeBytes) {
        // raw cape textures, used to show the cape on your own player
        capeBytes.forEach((id, bytes) -> {
            if (bytes == null) return;
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                Identifier tid = Identifier.fromNamespaceAndPath("capeswitcher", "cape/" + id.toLowerCase());
                Minecraft.getInstance().getTextureManager()
                        .register(tid, new DynamicTexture(() -> "capeswitcher cape", img));
                flatCapes.put(id, tid);
            } catch (Exception ignored) {
                // that cape just can't be shown locally
            }
        });

        // combined skin+cape textures for the 3D preview
        if (skin != null) {
            tryRegister("", skin, null);
            capeBytes.forEach((id, bytes) -> {
                if (bytes != null) tryRegister(id, skin, bytes);
            });
        }
        previewsDone = true;
    }

    private static void tryRegister(String key, byte[] skinBytes, byte[] capeBytes) {
        try {
            String path = "preview/" + (key.isEmpty() ? "none" : key.toLowerCase());
            previews.put(key, register(path, skinBytes, capeBytes));
        } catch (Exception ignored) {
            // this preview just won't be available
        }
    }

    /** Combined texture: skin 64x64 on top, cape 64x32 below (64x96 total, scaled by k for HD). */
    private static Identifier register(String path, byte[] skinBytes, byte[] capeBytes) throws Exception {
        NativeImage skin = NativeImage.read(new ByteArrayInputStream(skinBytes));
        NativeImage cape = capeBytes == null ? null : NativeImage.read(new ByteArrayInputStream(capeBytes));

        int k = Math.max(1, Math.max(skin.getWidth() / 64, cape == null ? 1 : cape.getWidth() / 64));
        NativeImage out = new NativeImage(64 * k, 96 * k, true);

        copyScaled(skin, out, 0, k);
        if (skin.getHeight() >= skin.getWidth()) {
            opaque(out, k, 0, 0, 32, 16);
            opaque(out, k, 0, 16, 64, 32);
            opaque(out, k, 16, 48, 48, 64);
        } else {
            opaque(out, k, 0, 0, 64, 32); // legacy 64x32 skin
        }
        if (cape != null) copyScaled(cape, out, 64 * k, k);

        skin.close();
        if (cape != null) cape.close();

        Identifier id = Identifier.fromNamespaceAndPath("capeswitcher", path);
        Minecraft.getInstance().getTextureManager()
                .register(id, new DynamicTexture(() -> "capeswitcher preview", out));
        return id;
    }

    private static void copyScaled(NativeImage src, NativeImage dst, int yOffset, int k) {
        int sw = src.getWidth(), sh = src.getHeight();
        int regionW = 64 * k;
        int regionH = sh * regionW / sw;
        for (int y = 0; y < regionH; y++) {
            if (yOffset + y >= dst.getHeight()) break;
            int sy = y * sh / regionH;
            for (int x = 0; x < regionW; x++) {
                int sx = x * sw / regionW;
                dst.setPixel(x, yOffset + y, src.getPixel(sx, sy));
            }
        }
    }

    private static void opaque(NativeImage img, int k, int x0, int y0, int x1, int y1) {
        for (int y = y0 * k; y < y1 * k; y++) {
            for (int x = x0 * k; x < x1 * k; x++) {
                img.setPixel(x, y, img.getPixel(x, y) | 0xFF000000);
            }
        }
    }

    public static void switchTo(String capeId, Runnable onDone) {
        if (switching || cooldownSeconds() > 0) return;
        switching = true;
        error = null;
        lastSwitch = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        String token = mc.getUser().getAccessToken();
        CapeApi.setActive(token, capeId).whenComplete((v, err) -> mc.execute(() -> {
            if (err != null) {
                error = rootMessage(err);
            } else {
                List<CapeApi.Cape> updated = new ArrayList<>();
                for (CapeApi.Cape c : capes) {
                    updated.add(new CapeApi.Cape(c.id(), c.alias(), c.url(), c.id().equals(capeId)));
                }
                capes = updated;
                switched = true;
                announce();
            }
            switching = false;
            onDone.run();
        }));
    }

    /** Chat message telling you what your client and the server show. */
    private static void announce() {
        if (!CapeConfig.localOverride) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String client = capeName(clientCapeId());
        String server = capeName(serverCapeId);
        MutableComponent msg = Component.literal("[Cape Switcher] ").withStyle(ChatFormatting.AQUA);
        if (Objects.equals(clientCapeId(), serverCapeId)) {
            msg.append(Component.literal("Client and server capes match: " + client)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            msg.append(Component.literal("Your capes are different. Client: " + client
                            + " | Server: " + server + ". Others will see " + server
                            + " until you rejoin a server.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        mc.player.sendSystemMessage(msg);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}