package com.example.capeswitcher;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CapeSettingsScreen extends Screen {
    private final Screen parent;

    public CapeSettingsScreen(Screen parent) {
        super(Component.literal("Cape Switcher Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        int titleW = this.font.width(this.title);
        addRenderableWidget(new StringWidget((this.width - titleW) / 2, 20, titleW, 9, this.title, this.font));

        addRenderableWidget(Button.builder(
                        Component.literal("Client-side cape: " + (CapeConfig.localOverride ? "ON" : "OFF")),
                        b -> {
                            CapeConfig.localOverride = !CapeConfig.localOverride;
                            CapeConfig.save();
                            rebuildWidgets();
                        })
                .bounds(cx - 100, 50, 200, 20).build());

        String[] lines = {
                "When on, your own player shows a newly selected cape",
                "immediately, and a chat message tells you when your",
                "client and server capes differ. Other players only see",
                "the new cape after you rejoin a server."
        };
        int y = 80;
        for (String line : lines) {
            int w = this.font.width(line);
            addRenderableWidget(new StringWidget((this.width - w) / 2, y, w, 9, Component.literal(line), this.font));
            y += 12;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}