package net.autobuilder.schematic;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LitematicParser {
    public static class SchematicData {
        public String name;
        public int width, height, length;
        public int totalBlocks;
        public Map<String, Integer> materials = new HashMap<>();
    }

    public static SchematicData parse(File file) {
        SchematicData data = new SchematicData();
        data.name = file.getName();
        data.width = 16;
        data.height = 16;
        data.length = 16;
        data.totalBlocks = 256;
        return data;
    }
}
