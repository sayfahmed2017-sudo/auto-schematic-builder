package dev.corz.builder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** CORZ AutoBuilder file browser. Safe first-stage UI: it never places blocks automatically. */
public final class AutoBuilderScreen extends Screen {
    private static final int PURPLE = 0xFFD51CFF;
    private static final int PANEL = 0xF0110F18;
    private static final int CARD = 0xFF201A2A;
    private static final int MUTED = 0xFFAAA0B8;
    private final Path folder;
    private final List<Path> files = new ArrayList<>();
    private String status = "Choose a .litematic file";
    private int selected = -1;
    private int page = 0;
    private int rotation = 0;
    private boolean mirrored = false;
    private boolean preview = false;
    private boolean materials = false;

    public AutoBuilderScreen() {
        super(Component.literal("CORZ AutoBuilder"));
        this.folder = Minecraft.getInstance().gameDirectory.toPath().resolve("schematics").resolve("donut");
        refreshFiles();
    }

    private void refreshFiles() {
        files.clear();
        try {
            Files.createDirectories(folder);
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litematic"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
            }
            if (selected >= files.size()) selected = files.isEmpty() ? -1 : files.size() - 1;
            status = files.isEmpty() ? "No .litematic files found" : files.size() + " schematic(s) found";
        } catch (IOException e) {
            status = "Could not read schematic folder";
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = Math.min(560, this.width - 24);
        int left = (this.width - w) / 2;
        int top = Math.max(12, (this.height - 340) / 2);
        int gap = 8;
        int bw = (w - 44) / 3;
        addRenderableWidget(Button.builder(Component.literal("Open Folder"), b -> {
            try { Files.createDirectories(folder); Util.getPlatform().openFile(folder.toFile()); status = "Opened schematics/donut"; }
            catch (Exception e) { status = "Could not open folder"; }
        }).bounds(left + 14, top + 72, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> { refreshFiles(); rebuildWidgets(); })
            .bounds(left + 14 + bw + gap, top + 72, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Materials"), b -> { materials = !materials; rebuildWidgets(); })
            .bounds(left + 14 + (bw + gap) * 2, top + 72, bw, 22).build());

        int listTop = top + 104;
        int visible = Math.max(1, Math.min(5, (this.height - listTop - 92) / 27));
        page = Math.min(page, Math.max(0, files.size() - visible));
        for (int i = page; i < Math.min(files.size(), page + visible); i++) {
            final int idx = i;
            String prefix = i == selected ? "◆  " : "◇  ";
            String name = files.get(i).getFileName().toString();
            if (name.length() > 58) name = name.substring(0, 55) + "...";
            addRenderableWidget(Button.builder(Component.literal(prefix + name), b -> {
                selected = idx;
                status = validateFile(files.get(idx));
                rebuildWidgets();
            }).bounds(left + 14, listTop + (i - page) * 27, w - 28, 23).build());
        }
        int controlsY = this.height - 66;
        int half = (w - 36) / 2;
        addRenderableWidget(Button.builder(Component.literal("◀ Previous"), b -> { if (!files.isEmpty()) { selected = Math.floorMod(selected - 1, files.size()); status = validateFile(files.get(selected)); rebuildWidgets(); } })
            .bounds(left + 14, controlsY, half, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Next ▶"), b -> { if (!files.isEmpty()) { selected = Math.floorMod(selected + 1, files.size()); status = validateFile(files.get(selected)); rebuildWidgets(); } })
            .bounds(left + 22 + half, controlsY, half, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Preview / Position"), b -> { preview = !preview; rebuildWidgets(); })
            .bounds(left + 14, controlsY + 26, half, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(left + 22 + half, controlsY + 26, half, 22).build());
    }

    private String validateFile(Path file) {
        try {
            long size = Files.size(file);
            if (size < 8) return "Invalid file: too small to be a schematic";
            if (size > 256L * 1024 * 1024) return "File rejected: larger than 256 MiB safety limit";
            try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(file))) {
                byte[] header = in.readNBytes(3);
                if (header.length < 3) return "Invalid compressed NBT header";
                return "File passed gzip/header checks • " + String.format(java.util.Locale.ROOT, "%.1f KB", size / 1024.0) + " • full NBT validation pending";
            }
        } catch (Exception e) {
            return "Invalid or unreadable .litematic file";
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int w = Math.min(560, this.width - 24);
        int left = (this.width - w) / 2;
        int top = Math.max(12, (this.height - 340) / 2);
        g.fill(left, top, left + w, this.height - 12, PANEL);
        g.fill(left, top, left + w, top + 3, PURPLE);
        g.drawString(font, "CORZ  /  AUTOBUILDER", left + 14, top + 13, PURPLE, true);
        g.drawString(font, "schematics/donut/", left + 14, top + 32, 0xFFFFFFFF, false);
        g.drawString(font, folder.toAbsolutePath().toString(), left + 14, top + 47, MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
        int statusY = this.height - 86;
        g.drawString(font, status, left + 14, statusY, status.startsWith("Invalid") || status.startsWith("Could") ? 0xFFFF7777 : MUTED, false);
        if (selected >= 0 && selected < files.size()) {
            g.drawString(font, "Selected: " + files.get(selected).getFileName(), left + 14, statusY + 12, 0xFFFFFFFF, false);
        }
        if (preview) {
            int px = left + w - 190, py = top + 108;
            g.fill(px, py, px + 176, py + 112, CARD);
            g.drawString(font, "PREVIEW / TRANSFORM", px + 8, py + 8, PURPLE, true);
            g.drawString(font, "Rotation: " + rotation + "°", px + 8, py + 26, 0xFFFFFFFF, false);
            g.drawString(font, "Mirror: " + (mirrored ? "ON" : "OFF"), px + 8, py + 40, 0xFFFFFFFF, false);
            g.drawString(font, "3D preview not loaded", px + 8, py + 62, MUTED, false);
        }
        if (materials) {
            int px = left + w - 190, py = top + 228;
            g.fill(px, py, px + 176, py + 70, CARD);
            g.drawString(font, "MATERIALS", px + 8, py + 8, PURPLE, true);
            g.drawString(font, "Needs full schematic decode", px + 8, py + 27, MUTED, false);
            g.drawString(font, "No counts guessed", px + 8, py + 42, MUTED, false);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
