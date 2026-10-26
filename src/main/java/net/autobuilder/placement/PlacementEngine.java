package net.autobuilder.placement;

import net.minecraft.client.MinecraftClient;

public class PlacementEngine {
    private boolean active = false;

    public void tick(MinecraftClient client) {
        if (!active || client.player == null || client.world == null) return;
    }

    public void start() { this.active = true; }
    public void stop() { this.active = false; }
    public boolean isActive() { return active; }
}
