package com.example.capeswitcher;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CapeScreen extends Screen {
    private static final int ROW = 24;
    private static final int LIST_W = 160;
    private static final float PIVOT_Y = 2.125F / 2.0F;
    // 180 = straight from the back. 135 = 45 degrees off the back (try 225 for the other side).
    private static final float VIEW_YAW = 135.0F;

    private final Screen parent;
    private final List<Button> items = new ArrayList<>();
    private double scroll = 0;
    private int listTop, listBottom;
    private int selected = -1; // 0 = no cape, 1..n = capes
    private boolean elytra = false;

    private int shownCooldown = -1;
    private boolean shownBusy = false;
    private boolean shownLoading = false;
    private boolean shownPreviews = false;

    public CapeScreen(Screen parent) {
        super(Component.literal("Cape Switcher"));
        this.parent = parent;
    }

    private Component status() {
        if (CapeManager.isLoading()) return Component.literal("Loading your capes...");
        if (CapeManager.error() != null) return Component.literal("Error: " + CapeManager.error());
        if (CapeManager.isSwitching()) return Component.literal("Updating...");
        if (CapeManager.cooldownSeconds() > 0)
            return Component.literal("Cooldown: " + CapeManager.cooldownSeconds() + "s");
        if (CapeManager.isLoaded() && CapeManager.capes().isEmpty())
            return Component.literal("You don't own any capes.");
        return Component.literal("Select a cape to preview, then press Apply");
    }

    private int itemCount() {
        return CapeManager.isLoaded() ? CapeManager.capes().size() + 1 : 0;
    }

    private int maxScroll() {
        return Math.max(0, itemCount() * ROW - (listBottom - listTop));
    }

    private int activeIndex() {
        List<CapeApi.Cape> capes = CapeManager.capes();
        for (int i = 0; i < capes.size(); i++) {
            if (capes.get(i).active()) return i + 1;
        }
        return 0;
    }

    private String selectedCapeId() {
        if (selected <= 0 || selected > CapeManager.capes().size()) return null;
        return CapeManager.capes().get(selected - 1).id();
    }

    private String label(int index, String name) {
        return (index == activeIndex() ? "\u2714 " : "") + (index == selected ? "> " : "") + name;
    }

    /** Preview panel is a square: the largest one that fits (max 170px), anchored at the top. */
    private int panelSize() {
        return Math.max(0, Math.min(170, listBottom - listTop));
    }

    private int panelX() {
        return this.width / 2 + 5 + (170 - panelSize()) / 2;
    }

    @Override
    protected void init() {
        items.clear();
        int cx = this.width / 2;
        listTop = 52;
        listBottom = this.height - 40;

        int titleW = this.font.width(this.title);
        addRenderableWidget(new StringWidget((this.width - titleW) / 2, 15, titleW, 9, this.title, this.font));
        Component status = status();
        int statusW = this.font.width(status);
        addRenderableWidget(new StringWidget((this.width - statusW) / 2, 28, statusW, 9, status, this.font));

        addRenderableWidget(Button.builder(Component.literal("Settings"),
                        b -> this.minecraft.setScreen(new CapeSettingsScreen(this)))
                .bounds(this.width - 66, 6, 60, 20).build());

        boolean canClick = CapeManager.isLoaded() && !CapeManager.isSwitching()
                && CapeManager.cooldownSeconds() == 0;

        if (CapeManager.isLoaded()) {
            if (selected < 0 || selected >= itemCount()) selected = activeIndex();
            List<CapeApi.Cape> capes = CapeManager.capes();
            int listX = cx - 175;

            items.add(addRenderableWidget(Button.builder(Component.literal(label(0, "No cape")),
                    b -> select(0)).bounds(listX, listTop, LIST_W, 20).build()));

            for (int i = 0; i < capes.size(); i++) {
                final int index = i + 1;
                items.add(addRenderableWidget(Button.builder(
                                Component.literal(label(index, capes.get(i).alias())),
                                b -> select(index))
                        .bounds(listX, listTop, LIST_W, 20).build()));
            }

            int size = panelSize();
            int btnW = Math.min(100, size - 10);
            Button elytraBtn = Button.builder(Component.literal(elytra ? "Elytra: On" : "Elytra: Off"),
                            b -> { elytra = !elytra; rebuildWidgets(); })
                    .bounds(panelX() + (size - btnW) / 2, listTop + size - 22, btnW, 20).build();
            elytraBtn.active = selectedCapeId() != null;
            addRenderableWidget(elytraBtn);
        } else if (!CapeManager.isLoading()) {
            addRenderableWidget(Button.builder(Component.literal("Retry"), b -> {
                CapeManager.fetchOnce();
                rebuildWidgets();
            }).bounds(cx - 100, listTop, 200, 20).build());
        }

        int cd = CapeManager.cooldownSeconds();
        Button apply = Button.builder(
                        Component.literal(cd > 0 ? "Apply (" + cd + "s)" : "Apply"),
                        b -> CapeManager.switchTo(selectedCapeId(), this::rebuildWidgets))
                .bounds(cx - 155, this.height - 30, 150, 20).build();
        apply.active = canClick && selected >= 0 && selected != activeIndex();
        addRenderableWidget(apply);

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 5, this.height - 30, 150, 20).build());

        layoutItems();

        shownCooldown = CapeManager.cooldownSeconds();
        shownBusy = CapeManager.isSwitching();
        shownLoading = CapeManager.isLoading();
        shownPreviews = CapeManager.previewsDone();
    }

    private void select(int index) {
        selected = index;
        if (selectedCapeId() == null) elytra = false;
        rebuildWidgets();
    }

    private void layoutItems() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (int i = 0; i < items.size(); i++) {
            Button b = items.get(i);
            int y = listTop + i * ROW - (int) scroll;
            b.setY(y);
            b.visible = y >= listTop && y + 20 <= listBottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll -= scrollY * 12;
            layoutItems();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!CapeManager.isLoaded()) return;

        Component capes = Component.literal("Client: " + CapeManager.capeName(CapeManager.clientCapeId())
                + "  |  Server: " + CapeManager.capeName(CapeManager.serverCapeId()));
        graphics.centeredText(this.font, capes, this.width / 2, 39, 0xFFAAAAAA);

        drawPreview(graphics);
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        int size = panelSize();
        int x0 = panelX(), x1 = x0 + size;
        int y0 = listTop, y1 = listTop + size;

        g.outline(x0, y0, size, size, 0xFF808080);
        g.text(this.font, "Preview", x0 + 4, y0 + 3, 0xFFFFFFFF, false);

        String capeId = selectedCapeId();
        Identifier tex = CapeManager.previewTexture(capeId);
        if (tex == null) {
            String msg = CapeManager.previewsDone() ? "Preview unavailable" : "Loading preview...";
            g.text(this.font, msg, x0 + 4, y0 + 16, 0xFFAAAAAA, false);
            return;
        }

        int ax0 = x0 + 6, ay0 = y0 + 14, ax1 = x1 - 6, ay1 = y1 - 26;
        if (ay1 - ay0 < 40) return;

        PlayerModel model = PreviewModel.get(CapeManager.isSlim(), capeId != null, elytra && capeId != null);
        float scale = 0.97F * (ay1 - ay0) / 2.125F;
        g.skin(model, tex, scale, -5.0F, VIEW_YAW, PIVOT_Y, ax0, ay0, ax1, ay1);
    }

    @Override
    public void tick() {
        super.tick();
        if (CapeManager.cooldownSeconds() != shownCooldown
                || CapeManager.isSwitching() != shownBusy
                || CapeManager.isLoading() != shownLoading
                || CapeManager.previewsDone() != shownPreviews) {
            rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}