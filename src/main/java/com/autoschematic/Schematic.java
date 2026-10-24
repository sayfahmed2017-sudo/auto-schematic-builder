package com.autoschematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A loaded schematic: a list of blocks relative to (0,0,0). Supports vanilla .nbt and Sponge .schem (v2/v3). */
public final class Schematic {
    public record Entry(BlockPos pos, BlockState state) {}

    public final String name;
    public int sizeX, sizeY, sizeZ;
    public int missing; // blocks that don't exist in this game version / modpack
    public final List<Entry> entries = new ArrayList<>();

    private Schematic(String name) {
        this.name = name;
    }

    public static Schematic load(Path path) throws IOException {
        String file = path.getFileName().toString();
        String lower = file.toLowerCase(Locale.ROOT);
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        Schematic s = new Schematic(file);
        if (lower.endsWith(".nbt")) {
            s.readVanilla(root);
        } else if (lower.endsWith(".schem")) {
            s.readSponge(root);
        } else {
            throw new IOException("Unsupported file type (use .nbt or .schem)");
        }
        if (s.entries.isEmpty()) {
            throw new IOException("Schematic has no blocks");
        }
        return s;
    }

    // ---------------------------------------------------------------- vanilla structure .nbt

    private void readVanilla(NbtCompound root) {
        NbtList size = root.getListOrEmpty("size");
        sizeX = size.getInt(0, 0);
        sizeY = size.getInt(1, 0);
        sizeZ = size.getInt(2, 0);

        NbtList paletteList = root.getListOrEmpty("palette");
        if (paletteList.isEmpty()) {
            paletteList = root.getListOrEmpty("palettes").getListOrEmpty(0);
        }
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteList.size(); i++) {
            NbtCompound c = paletteList.getCompoundOrEmpty(i);
            Map<String, String> props = new HashMap<>();
            NbtCompound pc = c.getCompoundOrEmpty("Properties");
            for (String key : pc.getKeys()) {
                props.put(key, pc.getString(key, ""));
            }
            palette.add(parseState(c.getString("Name", "minecraft:air"), props));
        }

        NbtList blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound b = blocks.getCompoundOrEmpty(i);
            int idx = b.getInt("state", 0);
            if (idx < 0 || idx >= palette.size()) continue;
            NbtList p = b.getListOrEmpty("pos");
            add(new BlockPos(p.getInt(0, 0), p.getInt(1, 0), p.getInt(2, 0)), palette.get(idx));
        }
        finish();
    }

    // ---------------------------------------------------------------- Sponge .schem

    private void readSponge(NbtCompound root) throws IOException {
        NbtCompound s = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root; // v3 wraps in "Schematic"
        int w = s.getInt("Width", 0), h = s.getInt("Height", 0), l = s.getInt("Length", 0);
        sizeX = w;
        sizeY = h;
        sizeZ = l;

        NbtCompound paletteTag;
        byte[] data;
        if (s.contains("Blocks")) { // v3
            NbtCompound b = s.getCompoundOrEmpty("Blocks");
            paletteTag = b.getCompoundOrEmpty("Palette");
            data = b.getByteArray("Data").orElse(new byte[0]);
        } else { // v2
            paletteTag = s.getCompoundOrEmpty("Palette");
            data = s.getByteArray("BlockData").orElse(new byte[0]);
        }
        if (w <= 0 || h <= 0 || l <= 0 || data.length == 0) {
            throw new IOException("Invalid or empty .schem file");
        }

        Map<Integer, BlockState> palette = new HashMap<>();
        for (String key : paletteTag.getKeys()) {
            palette.put(paletteTag.getInt(key, 0), parseStateString(key));
        }

        int pos = 0;
        int total = w * h * l;
        for (int i = 0; i < total && pos < data.length; i++) {
            int value = 0, shift = 0;
            while (pos < data.length) {
                byte b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            BlockState st = palette.get(value);
            if (st == null) continue;
            int x = i % w;
            int z = (i / w) % l;
            int y = i / (w * l);
            add(new BlockPos(x, y, z), st);
        }
        finish();
    }

    // ---------------------------------------------------------------- helpers

    private void add(BlockPos pos, BlockState state) {
        if (state.isAir()) return;
        if (state.isOf(Blocks.STRUCTURE_VOID)) return;
        entries.add(new Entry(pos, state));
    }

    private void finish() {
        // Bottom-up so supports are placed before the blocks that need them.
        entries.sort((a, b) -> {
            int c = Integer.compare(a.pos().getY(), b.pos().getY());
            if (c != 0) return c;
            c = Integer.compare(a.pos().getZ(), b.pos().getZ());
            if (c != 0) return c;
            return Integer.compare(a.pos().getX(), b.pos().getX());
        });
    }

    /** "minecraft:oak_stairs[facing=north,half=bottom]" */
    private BlockState parseStateString(String s) {
        Map<String, String> props = new HashMap<>();
        String id = s;
        int br = s.indexOf('[');
        if (br >= 0 && s.endsWith("]")) {
            id = s.substring(0, br);
            for (String part : s.substring(br + 1, s.length() - 1).split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0) props.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        return parseState(id, props);
    }

    private BlockState parseState(String id, Map<String, String> props) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null || !Registries.BLOCK.containsId(ident)) {
            missing++;
            return Blocks.AIR.getDefaultState();
        }
        Block block = Registries.BLOCK.get(ident);
        BlockState state = block.getDefaultState();
        for (Map.Entry<String, String> e : props.entrySet()) {
            Property<?> p = block.getStateManager().getProperty(e.getKey());
            if (p != null) state = applyProperty(state, p, e.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> p, String value) {
        return p.parse(value).map(v -> state.with(p, v)).orElse(state);
    }
}
