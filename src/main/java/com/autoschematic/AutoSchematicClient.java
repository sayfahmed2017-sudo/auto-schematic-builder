package com.autoschematic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoSchematicClient implements ClientModInitializer {
    public static final String MOD_ID = "autoschematic";

    private static KeyBinding openKey;
    private static KeyBinding pauseKey;

    public static Path schematicsDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autoschematic.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, category));
        pauseKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autoschematic.pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, category));

        schematicsDir();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.world != null) {
                    client.setScreen(new BuilderScreen());
                }
            }
            while (pauseKey.wasPressed()) {
                BuildManager.togglePause(client);
            }
            BuildManager.tick(client);
        });
    }
}
