package com.autoschematic;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class BuilderScreen extends Screen {
    private static final int PER_PAGE = 8;
    private static final BlockRotation[] ROTS = {
            BlockRotation.NONE, BlockRotation.CLOCKWISE_90,
            BlockRotation.CLOCKWISE_180, BlockRotation.COUNTERCLOCKWISE_90};
    private static final int[] ROT_DEG = {0, 90, 180, 270};

    // State survives closing/reopening the screen.
    private static String selected = null;
    private static Schematic loaded = null;
    private static String message = "";
    private static int page = 0;
    private static int rotIndex = 0;
    private static int directSpeed = 50;   // blocks per tick
    private static int commandSpeed = 2;   // commands per second
    private static String xs = "", ys = "", zs = "";

    private final List<Path> files = new ArrayList<>();
    private TextFieldWidget xField, yField, zField;

    public BuilderScreen() {
        super(Text.literal("Auto Schematic Builder"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static boolean directMode() {
        return BuildManager.isDirectMode(MinecraftClient.getInstance());
    }

    @Override
    protected void init() {
        scanFiles();
        int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        // ---- left: schematic list
        int left = 10, top = 40, listW = 160;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= files.size()) break;
            Path p = files.get(idx);
            String name = p.getFileName().toString();
            String label = (name.equals(selected) ? "> " : "") + (name.length() > 22 ? name.substring(0, 21) + "…" : name);
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> select(p))
                    .dimensions(left, top + i * 22, listW, 20).build());
        }
        int navY = top + PER_PAGE * 22 + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                .dimensions(left, navY, 30, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> clearAndInit())
                .dimensions(left + 34, navY, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                .dimensions(left + 130, navY, 30, 20).build()).active = page < pages - 1;

        // ---- right: options
        int x0 = 190;
        int rw = Math.min(220, this.width - x0 - 10);

        if (xs.isEmpty() && client != null && client.player != null) {
            BlockPos pp = client.player.getBlockPos();
            xs = String.valueOf(pp.getX());
            ys = String.valueOf(pp.getY());
            zs = String.valueOf(pp.getZ());
        }
        xField = coordField(x0, 80, xs, v -> xs = v);
        yField = coordField(x0 + 56, 80, ys, v -> ys = v);
        zField = coordField(x0 + 112, 80, zs, v -> zs = v);
        addDrawableChild(ButtonWidget.builder(Text.literal("Here"), b -> useMyPosition())
                .dimensions(x0 + 168, 80, Math.max(30, rw - 168), 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Rotation: " + ROT_DEG[rotIndex] + "°"), b -> {
            rotIndex = (rotIndex + 1) % ROTS.length;
            clearAndInit();
        }).dimensions(x0, 106, rw, 20).build());

        addDrawableChild(new SpeedSlider(x0, 130, rw, 20));

        addDrawableChild(ButtonWidget.builder(Text.literal("Start build"), b -> startBuild())
                .dimensions(x0, 156, rw, 20).build());
        int half = (rw - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Pause / Resume"), b -> BuildManager.togglePause(client))
                .dimensions(x0, 180, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> BuildManager.cancel(client))
                .dimensions(x0 + half + 4, 180, half, 20).build());
    }

    private TextFieldWidget coordField(int x, int y, String value, java.util.function.Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 52, 20, Text.empty());
        f.setMaxLength(9);
        f.setText(value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        return f;
    }

    private void useMyPosition() {
        if (client == null || client.player == null) return;
        BlockPos pp = client.player.getBlockPos();
        xField.setText(String.valueOf(pp.getX()));
        yField.setText(String.valueOf(pp.getY()));
        zField.setText(String.valueOf(pp.getZ()));
    }

    private void scanFiles() {
        files.clear();
        try (Stream<Path> s = Files.list(AutoSchematicClient.schematicsDir())) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".nbt") || n.endsWith(".schem");
            }).sorted().forEach(files::add);
        } catch (IOException e) {
            message = "Could not read schematics folder";
        }
    }

    private void select(Path p) {
        selected = p.getFileName().toString();
        try {
            loaded = Schematic.load(p);
            message = "";
        } catch (Exception e) {
            loaded = null;
            message = "Failed to load: " + e.getMessage();
        }
        clearAndInit();
    }

    private void startBuild() {
        if (loaded == null) {
            message = "Select a schematic first";
            return;
        }
        try {
            BlockPos origin = new BlockPos(
                    Integer.parseInt(xField.getText().trim()),
                    Integer.parseInt(yField.getText().trim()),
                    Integer.parseInt(zField.getText().trim()));
            int speed = directMode() ? directSpeed : commandSpeed;
            BuildManager.start(client, loaded, origin, ROTS[rotIndex], speed);
            close();
        } catch (NumberFormatException e) {
            message = "Coordinates must be whole numbers";
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int white = 0xFFFFFFFF, grey = 0xFFAAAAAA, red = 0xFFFF5555, green = 0xFF55FF55;
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, white);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Put .nbt / .schem files in: .minecraft/schematics"), 10, 26, grey);

        int x0 = 190;
        if (loaded != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(loaded.name), x0, 40, white);
            ctx.drawTextWithShadow(textRenderer, Text.literal(loaded.sizeX + "x" + loaded.sizeY + "x" + loaded.sizeZ
                    + "  |  " + loaded.entries.size() + " blocks"), x0, 52, grey);
            if (loaded.missing > 0) {
                ctx.drawTextWithShadow(textRenderer, Text.literal(loaded.missing + " unknown blocks skipped"), x0, 64, red);
            }
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal("No schematic selected"), x0, 40, grey);
        }
        ctx.drawTextWithShadow(textRenderer, Text.literal("Corner position  X          Y          Z"), x0, 68, grey);

        ctx.drawTextWithShadow(textRenderer, Text.literal(directMode()
                ? "Mode: Direct (singleplayer, no cheats needed)"
                : "Mode: Commands (needs OP, slow)"), x0, 206, directMode() ? green : 0xFFFFAA00);
        ctx.drawTextWithShadow(textRenderer, Text.literal(BuildManager.status()), x0, 218, white);
        if (!message.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(message), x0, 230, red);
        }
    }

    private static class SpeedSlider extends SliderWidget {
        SpeedSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), initial());
            updateMessage();
        }

        private static int min() { return 1; }
        private static int max() { return directMode() ? 500 : 20; }

        private static double initial() {
            int v = directMode() ? directSpeed : commandSpeed;
            return (v - min()) / (double) (max() - min());
        }

        @Override
        protected void updateMessage() {
            int v = current();
            setMessage(Text.literal(directMode() ? "Speed: " + v + " blocks/tick" : "Speed: " + v + " commands/sec"));
        }

        private int current() {
            return min() + (int) Math.round(this.value * (max() - min()));
        }

        @Override
        protected void applyValue() {
            if (directMode()) directSpeed = current();
            else commandSpeed = current();
        }
    }
}
