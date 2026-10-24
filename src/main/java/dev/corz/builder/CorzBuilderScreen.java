package dev.corz.builder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CorzBuilderScreen extends Screen {
    private static final int PURPLE = 0xFFD51CFF;
    private static final int PANEL = 0xF0110F18;
    private static final int CARD = 0xFF191521;
    private static final int MUTED = 0xFF9A90A5;
    private final Map<String, Boolean> modules = new LinkedHashMap<>();

    public CorzBuilderScreen() {
        super(Component.literal("CORZ Builder"));
        modules.put("Auto Schematic Builder", true);
        modules.put("Aim Assist", false);
        modules.put("Auto Crystal", false);
        modules.put("Auto Anchors", false);
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelWidth = Math.min(430, this.width - 30);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(24, (this.height - 300) / 2);

        int y = top + 91;
        int cardHeight = 37;
        for (String name : modules.keySet()) {
            boolean enabled = modules.get(name);
            String label = name + "   [" + (enabled ? "ON" : "OFF") + "]";
            Button button = Button.builder(Component.literal(label), b -> {
                modules.put(name, !modules.get(name));
                rebuildWidgets();
            }).bounds(left + 22, y, panelWidth - 44, cardHeight).build();
            addRenderableWidget(button);
            y += 45;
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + 22, y + 2, panelWidth - 44, 25).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(430, this.width - 30);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(24, (this.height - 300) / 2);

        graphics.fill(left, top, left + panelWidth, top + 300, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 3, PURPLE);
        graphics.drawString(this.font, "CORZ", left + 22, top + 17, PURPLE, true);
        graphics.drawString(this.font, "BUILDER DASHBOARD", left + 22, top + 34, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "RIGHT SHIFT  •  MODULE SETTINGS", left + 22, top + 59, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font,
                "UI prototype — toggles do not automate gameplay",
                this.width / 2, Math.min(this.height - 12, top + 286), MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
