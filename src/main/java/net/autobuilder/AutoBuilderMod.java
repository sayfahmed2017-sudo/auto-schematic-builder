package net.autobuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AutoBuilderMod implements ClientModInitializer {
    public static KeyBinding openMenuKey;
    private static boolean enabled = false;

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autobuilder.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "category.autobuilder.title"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("[AutoBuilder] Menu toggled: " + (enabled ? "ENABLED" : "DISABLED")),
                        true
                    );
                }
            }
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
