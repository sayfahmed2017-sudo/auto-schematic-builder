package com.autoschematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the build.
 *  - Singleplayer: places blocks directly on the integrated server (fast, no cheats needed).
 *  - Multiplayer: sends /setblock and /fill commands (needs OP, heavily throttled).
 */
public final class BuildManager {
    private record Placement(BlockPos pos, BlockState state) {}

    private static List<Placement> queue = List.of();
    private static List<String> commands = List.of();
    private static boolean direct;
    private static boolean running;
    private static boolean paused;
    private static int index;
    private static int total;
    private static int speed;
    private static int tickCounter;
    private static int msgCounter;
    private static String schematicName = "";

    private BuildManager() {}

    public static boolean isRunning() { return running; }
    public static boolean isPaused() { return paused; }

    public static boolean isDirectMode(MinecraftClient client) {
        return client.getServer() != null;
    }

    public static String status() {
        if (!running) return "Idle";
        int pct = total == 0 ? 100 : index * 100 / total;
        return (paused ? "Paused" : "Building") + " " + schematicName + ": " + pct + "% (" + index + "/" + total + ")";
    }

    /**
     * @param speed direct mode: blocks per tick; command mode: commands per second (1-20)
     */
    public static void start(MinecraftClient client, Schematic s, BlockPos origin, BlockRotation rot, int speed) {
        // Rotate, then normalise so the schematic's min corner sits on `origin`.
        List<BlockPos> rotated = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (Schematic.Entry e : s.entries) {
            BlockPos r = e.pos().rotate(rot);
            rotated.add(r);
            minX = Math.min(minX, r.getX());
            minZ = Math.min(minZ, r.getZ());
        }
        List<Placement> list = new ArrayList<>(s.entries.size());
        for (int i = 0; i < s.entries.size(); i++) {
            BlockPos r = rotated.get(i);
            BlockPos abs = origin.add(r.getX() - minX, r.getY(), r.getZ() - minZ);
            list.add(new Placement(abs, s.entries.get(i).state().rotate(rot)));
        }
        list.sort(Comparator
                .comparingInt((Placement p) -> p.pos().getY())
                .thenComparingInt(p -> p.pos().getZ())
                .thenComparingInt(p -> p.pos().getX()));

        direct = isDirectMode(client);
        BuildManager.speed = Math.max(1, speed);
        queue = list;
        commands = direct ? List.of() : buildCommands(list);
        total = direct ? list.size() : commands.size();
        index = 0;
        tickCounter = 0;
        msgCounter = 0;
        schematicName = s.name;
        paused = false;
        running = true;
        say(client, "Started building " + s.name + " (" + list.size() + " blocks)");
    }

    public static void cancel(MinecraftClient client) {
        if (running) say(client, "Build cancelled");
        running = false;
        paused = false;
        queue = List.of();
        commands = List.of();
    }

    public static void togglePause(MinecraftClient client) {
        if (!running) return;
        paused = !paused;
        say(client, paused ? "Build paused" : "Build resumed");
    }

    public static void tick(MinecraftClient client) {
        if (!running || paused) return;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            running = false;
            return;
        }

        if (direct) {
            IntegratedServer server = client.getServer();
            if (server == null) {
                running = false;
                return;
            }
            int end = Math.min(index + speed, total);
            List<Placement> batch = new ArrayList<>(queue.subList(index, end));
            index = end;
            RegistryKey<World> key = client.world.getRegistryKey();
            server.execute(() -> {
                ServerWorld w = server.getWorld(key);
                if (w == null) return;
                for (Placement p : batch) {
                    w.setBlockState(p.pos(), p.state(), Block.NOTIFY_LISTENERS);
                }
            });
        } else {
            tickCounter++;
            int interval = Math.max(1, 20 / speed);
            if (tickCounter >= interval) {
                tickCounter = 0;
                player.networkHandler.sendChatCommand(commands.get(index++));
            }
        }

        if (index >= total) {
            running = false;
            say(client, "Finished building " + schematicName);
            return;
        }
        if (++msgCounter >= 10) {
            msgCounter = 0;
            player.sendMessage(Text.literal(status()), true);
        }
    }

    // ------------------------------------------------------------ command mode helpers

    /** Merges runs of identical blocks along X into /fill commands to cut the command count. */
    private static List<String> buildCommands(List<Placement> list) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            Placement a = list.get(i);
            int j = i;
            while (j + 1 < list.size()) {
                Placement last = list.get(j);
                Placement next = list.get(j + 1);
                if (next.state() == a.state()
                        && next.pos().getY() == a.pos().getY()
                        && next.pos().getZ() == a.pos().getZ()
                        && next.pos().getX() == last.pos().getX() + 1) {
                    j++;
                } else {
                    break;
                }
            }
            String st = stateString(a.state());
            BlockPos p1 = a.pos();
            BlockPos p2 = list.get(j).pos();
            if (j == i) {
                out.add("setblock " + p1.getX() + " " + p1.getY() + " " + p1.getZ() + " " + st);
            } else {
                out.add("fill " + p1.getX() + " " + p1.getY() + " " + p1.getZ() + " "
                        + p2.getX() + " " + p2.getY() + " " + p2.getZ() + " " + st);
            }
            i = j + 1;
        }
        return out;
    }

    private static String stateString(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        if (state.getEntries().isEmpty()) return id;
        String props = state.getEntries().entrySet().stream()
                .map(e -> e.getKey().getName() + "=" + valueName(e.getKey(), e.getValue()))
                .collect(Collectors.joining(","));
        return id + "[" + props + "]";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property<?> p, Comparable<?> v) {
        return ((Property) p).name(v);
    }

    private static void say(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Schematic Builder] " + msg), false);
        }
    }
}
