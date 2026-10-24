package com.autoschematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Runs the build.
 *  - Singleplayer, creative-style: places blocks directly on the integrated server.
 *  - Singleplayer, survival mode: takes blocks from your inventory and only places a block
 *    when it touches an existing block (never floating in the air).
 *  - Multiplayer: sends /setblock and /fill commands (needs OP, heavily throttled).
 */
public final class BuildManager {
    private record Placement(BlockPos pos, BlockState state) {}

    private static volatile List<Placement> queue = List.of();
    private static volatile List<Placement> deferred = new ArrayList<>();
    private static List<String> commands = List.of();
    private static volatile boolean direct;
    private static volatile boolean survival;
    private static volatile boolean running;
    private static volatile boolean paused;
    private static volatile boolean passProgress;
    private static volatile int index;
    private static volatile int done;
    private static volatile int total;
    private static volatile int skippedItems;
    private static volatile String pendingMessage;
    private static int speed;
    private static int tickCounter;
    private static int msgCounter;
    private static double accumulator;
    private static String schematicName = "";
    private static final AtomicBoolean serverBusy = new AtomicBoolean(false);

    private BuildManager() {}

    public static boolean isRunning() { return running; }
    public static boolean isPaused() { return paused; }

    public static boolean isDirectMode(MinecraftClient client) {
        return client.getServer() != null;
    }

    public static String status() {
        if (!running) return "Idle";
        int pct = total == 0 ? 100 : done * 100 / total;
        return (paused ? "Paused" : "Building") + " " + schematicName + ": " + pct + "% (" + done + "/" + total + ")";
    }

    /**
     * @param speed direct mode: blocks per second; command mode: commands per second (1-20)
     */
    public static void start(MinecraftClient client, Schematic s, BlockPos origin, BlockRotation rot, int speed, boolean useSurvival) {
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
        survival = direct && useSurvival;
        BuildManager.speed = Math.max(1, speed);
        queue = list;
        deferred = new ArrayList<>();
        commands = direct ? List.of() : buildCommands(list);
        total = direct ? list.size() : commands.size();
        index = 0;
        done = 0;
        skippedItems = 0;
        passProgress = false;
        tickCounter = 0;
        accumulator = 0;
        msgCounter = 0;
        pendingMessage = null;
        schematicName = s.name;
        paused = false;
        running = true;
        say(client, "Started building " + s.name + " (" + list.size() + " blocks)"
                + (survival ? " using your inventory" : ""));
    }

    public static void cancel(MinecraftClient client) {
        if (running) say(client, "Build cancelled");
        running = false;
        paused = false;
        queue = List.of();
        deferred = new ArrayList<>();
        commands = List.of();
    }

    public static void togglePause(MinecraftClient client) {
        if (!running) return;
        paused = !paused;
        say(client, paused ? "Build paused" : "Build resumed");
    }

    public static void tick(MinecraftClient client) {
        String m = pendingMessage;
        if (m != null) {
            pendingMessage = null;
            say(client, m);
        }
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
            accumulator += speed / 20.0;
            int count = (int) accumulator;
            RegistryKey<World> key = client.world.getRegistryKey();
            if (survival) {
                if (count > 0 && serverBusy.compareAndSet(false, true)) {
                    accumulator -= count;
                    UUID uuid = player.getUuid();
                    int budget = count;
                    server.execute(() -> {
                        try {
                            survivalStep(server, key, uuid, budget);
                        } finally {
                            serverBusy.set(false);
                        }
                    });
                }
            } else if (count > 0) {
                accumulator -= count;
                int end = Math.min(index + count, total);
                List<Placement> batch = new ArrayList<>(queue.subList(index, end));
                index = end;
                done = end;
                server.execute(() -> {
                    ServerWorld w = server.getWorld(key);
                    if (w == null) return;
                    for (Placement p : batch) {
                        w.setBlockState(p.pos(), p.state(), Block.NOTIFY_LISTENERS);
                    }
                    if (batch.size() <= 2) placeSound(w, batch.get(0));
                });
            }
        } else {
            tickCounter++;
            int interval = Math.max(1, 20 / speed);
            if (tickCounter >= interval) {
                tickCounter = 0;
                player.networkHandler.sendChatCommand(commands.get(index++));
                done = index;
            }
        }

        if (!survival && index >= total) {
            running = false;
            say(client, "Finished building " + schematicName);
            return;
        }
        if (++msgCounter >= 10) {
            msgCounter = 0;
            player.sendMessage(Text.literal(status()), true);
        }
    }

    // ------------------------------------------------------------ survival mode (server thread)

    private static void survivalStep(IntegratedServer server, RegistryKey<World> key, UUID uuid, int budget) {
        ServerWorld w = server.getWorld(key);
        ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
        if (w == null || sp == null) return;
        boolean creative = sp.isCreative();
        int scans = 0;

        while (budget > 0 && running && !paused) {
            if (++scans > 2000) return; // keep each tick cheap; continue next tick
            List<Placement> q = queue;

            if (index >= q.size()) {
                // End of a pass. Blocks that had nothing to attach to get another chance
                // once other blocks have been placed.
                List<Placement> d = deferred;
                if (!d.isEmpty() && passProgress) {
                    queue = d;
                    deferred = new ArrayList<>();
                    index = 0;
                    passProgress = false;
                    continue;
                }
                running = false;
                int left = d.size();
                pendingMessage = "Finished " + schematicName
                        + (left > 0 ? " - " + left + " blocks had nothing to attach to" : "")
                        + (skippedItems > 0 ? " - " + skippedItems + " blocks can't be obtained as items" : "");
                return;
            }

            Placement p = q.get(index);
            BlockPos pos = p.pos();

            if (!w.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                return; // wait until the player is close enough for the chunk to load
            }
            if (w.getBlockState(pos) == p.state()) { // already there
                index++;
                done++;
                passProgress = true;
                continue;
            }
            if (!supported(w, pos)) { // would float in the air: try again later
                deferred.add(p);
                index++;
                continue;
            }

            int cost = creative ? 0 : cost(p.state());
            if (cost > 0) {
                Item item = p.state().getBlock().asItem();
                if (item == Items.AIR) { // e.g. water, fire: no item exists
                    skippedItems++;
                    index++;
                    done++;
                    passProgress = true;
                    continue;
                }
                if (!take(sp, item, cost)) {
                    paused = true;
                    pendingMessage = "Out of " + item.getName().getString()
                            + ". Add more to your inventory, then press N to resume.";
                    return;
                }
            }

            w.setBlockState(pos, p.state(), Block.NOTIFY_LISTENERS);
            placeSound(w, p);
            index++;
            done++;
            passProgress = true;
            budget--;
        }
    }

    private static boolean supported(ServerWorld w, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState s = w.getBlockState(pos.offset(d));
            if (!s.isAir() && s.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** How many items one placement costs (upper door halves are free, double slabs cost 2). */
    private static int cost(BlockState s) {
        if (s.contains(Properties.DOUBLE_BLOCK_HALF) && s.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return 0;
        if (s.contains(Properties.BED_PART) && s.get(Properties.BED_PART) == BedPart.HEAD) return 0;
        if (s.contains(Properties.SLAB_TYPE) && s.get(Properties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        return 1;
    }

    private static boolean take(ServerPlayerEntity sp, Item item, int amount) {
        PlayerInventory inv = sp.getInventory();
        int have = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) have += st.getCount();
        }
        if (have < amount) return false;
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) {
                int t = Math.min(amount, st.getCount());
                st.decrement(t);
                amount -= t;
            }
        }
        inv.markDirty();
        return true;
    }

    private static void placeSound(ServerWorld w, Placement p) {
        w.playSound(null, p.pos(), p.state().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0f, 0.8f);
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
