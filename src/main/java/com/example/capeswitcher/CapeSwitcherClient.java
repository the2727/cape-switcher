package com.example.capeswitcher;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class CapeSwitcherClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        CapeConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("capeswitcher", "main"));

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.capeswitcher.open", InputConstants.KEY_J, category));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> CapeManager.fetchOnce());

        // Servers read your cape when you join, so this is what others see from now on
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CapeManager.snapshotServerCape());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                mc.setScreen(new CapeScreen(mc.screen));
            }
        });
    }
}