package relic.client.module.impl.visual;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HoleESPModule extends Module {

    private static HoleESPModule instance;

    private static final int HOLE_1X1  = 0;
    private static final int HOLE_3X1  = 1;
    private static final int TUNNEL    = 2;
    private static final int DIAGONAL  = 3;
    private static final int STAIRS    = 4;

    private static final long RESCAN_COOLDOWN_MS = 750L;

    private final BooleanSetting holes    = new BooleanSetting("Holes (1x1)", true);
    private final BooleanSetting holes3x1  = new BooleanSetting("Holes (3x1)", true);
    private final BooleanSetting tunnels   = new BooleanSetting("Tunnels", true);
    private final BooleanSetting diagonals = new BooleanSetting("Diagonal Tunnels", true);
    private final BooleanSetting stairs    = new BooleanSetting("Staircases", true);

    private final BooleanSetting airOnly   = new BooleanSetting("Air Only", true);

    private final NumberSetting minHoleDepth = new NumberSetting("Min Hole Depth", 3, 2, 32, true);

    private final NumberSetting minTunnelLen    = new NumberSetting("Min Tunnel Length", 6, 2, 64, true);
    private final NumberSetting minTunnelHeight  = new NumberSetting("Min Tunnel Height", 1, 1, 10, true);
    private final NumberSetting maxTunnelHeight  = new NumberSetting("Max Tunnel Height", 3, 1, 10, true);
    private final NumberSetting minTunnelWidth   = new NumberSetting("Min Tunnel Width", 1, 1, 8, true);
    private final NumberSetting maxTunnelWidth   = new NumberSetting("Max Tunnel Width", 3, 1, 8, true);
    private final NumberSetting minDiagLen       = new NumberSetting("Min Diagonal Length", 6, 3, 64, true);

    private final NumberSetting minStairLen    = new NumberSetting("Min Staircase Length", 3, 2, 32, true);
    private final NumberSetting minStairHeight  = new NumberSetting("Min Staircase Height", 2, 2, 10, true);
    private final NumberSetting maxStairHeight  = new NumberSetting("Max Staircase Height", 4, 2, 10, true);

    private final NumberSetting minY = new NumberSetting("Min Y", -64, -64, 320, true);
    private final NumberSetting maxY = new NumberSetting("Max Y", 120, -64, 320, true);

    private final ModeSetting renderMode = new ModeSetting("Render", "Both", "Both", "Filled", "Outline");
    private final ColorSetting holeColor   = new ColorSetting("Hole Color",     1.0f, 0.15f, 0.15f, 0.85f);
    private final ColorSetting hole3Color   = new ColorSetting("Hole 3x1 Color", 1.0f, 0.6f,  0.1f,  0.85f);
    private final ColorSetting tunnelColor  = new ColorSetting("Tunnel Color",   0.2f, 0.45f, 1.0f,  0.85f);
    private final ColorSetting diagColor    = new ColorSetting("Diagonal Color", 0.2f, 0.9f,  0.9f,  0.85f);
    private final ColorSetting stairColor   = new ColorSetting("Staircase Color",1.0f, 0.25f, 1.0f,  0.85f);

    private record Detection(Box box, int type) {}

    private final Long2ObjectOpenHashMap<List<Detection>> chunks = new Long2ObjectOpenHashMap<>();

    private final LongOpenHashSet dirtyChunks = new LongOpenHashSet();

    private final Long2LongOpenHashMap lastScanMs = new Long2LongOpenHashMap();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Relic-HoleESP-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private RegistryKey<World> lastDimension;

    public HoleESPModule() {
        super("HoleESP", "Highlights holes, tunnels and staircases", Category.VISUAL);
        addSettings(holes, holes3x1, tunnels, diagonals, stairs, airOnly,
                minHoleDepth,
                minTunnelLen, minTunnelHeight, maxTunnelHeight, minTunnelWidth, maxTunnelWidth, minDiagLen,
                minStairLen, minStairHeight, maxStairHeight,
                minY, maxY,
                renderMode, holeColor, hole3Color, tunnelColor, diagColor, stairColor);
        instance = this;

        Setting<?>[] rescanTriggers = {holes, holes3x1, tunnels, diagonals, stairs, airOnly,
                minHoleDepth, minTunnelLen, minTunnelHeight, maxTunnelHeight, minTunnelWidth, maxTunnelWidth,
                minDiagLen, minStairLen, minStairHeight, maxStairHeight, minY, maxY};
        for (Setting<?> s : rescanTriggers) s.onChanged(this::fullRescan);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled()) scanChunkAsync(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            synchronized (chunks) {
                chunks.remove(key);
            }
            dirtyChunks.remove(key);
            lastScanMs.remove(key);
        });
    }

    public static HoleESPModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        fullRescan();
    }

    @Override
    protected void onDisable() {
        synchronized (chunks) {
            chunks.clear();
        }
        dirtyChunks.clear();
        lastScanMs.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (lastDimension != null && lastDimension != dimension) {
            fullRescan();
        }
        lastDimension = dimension;

        if (dirtyChunks.isEmpty()) return;
        long now = System.currentTimeMillis();
        LongIterator it = dirtyChunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (now - lastScanMs.get(key) < RESCAN_COOLDOWN_MS) continue;
            it.remove();
            lastScanMs.put(key, now);
            ChunkPos cp = new ChunkPos(key);
            Chunk chunk = mc.world.getChunkManager()
                    .getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (chunk instanceof WorldChunk worldChunk) scanChunkAsync(worldChunk);
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        List<BoxRenderer.ColoredBox> list = new ArrayList<>();
        synchronized (chunks) {
            for (List<Detection> dets : chunks.values()) {
                for (Detection d : dets) {
                    ColorSetting c = colorFor(d.type());
                    list.add(new BoxRenderer.ColoredBox(d.box(), c.red(), c.green(), c.blue(), c.alpha()));
                }
            }
        }
        if (!list.isEmpty()) BoxRenderer.draw(list, resolveMode());
    }

    public void onBlockUpdate(BlockPos pos) {
        if (!isEnabled()) return;
        dirtyChunks.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    private ColorSetting colorFor(int type) {
        return switch (type) {
            case HOLE_3X1 -> hole3Color;
            case TUNNEL   -> tunnelColor;
            case DIAGONAL -> diagColor;
            case STAIRS   -> stairColor;
            default       -> holeColor;
        };
    }

    private BoxRenderer.Mode resolveMode() {
        return switch (renderMode.getValue()) {
            case "Filled"  -> BoxRenderer.Mode.FILLED;
            case "Outline" -> BoxRenderer.Mode.OUTLINED;
            default        -> BoxRenderer.Mode.BOTH;
        };
    }

    private void fullRescan() {
        if (!isEnabled()) return;
        synchronized (chunks) {
            chunks.clear();
        }
        dirtyChunks.clear();
        lastScanMs.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        int viewDistance = mc.options.getViewDistance().getValue();
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                Chunk chunk = mc.world.getChunkManager()
                        .getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk worldChunk) scanChunkAsync(worldChunk);
            }
        }
    }

    private void scanChunkAsync(WorldChunk chunk) {

        Config cfg = snapshot();
        long key = chunk.getPos().toLong();
        if (!cfg.anyEnabled()) {
            synchronized (chunks) {
                chunks.remove(key);
            }
            return;
        }

        WORKER.submit(() -> {
            if (!isEnabled()) return;
            List<Detection> found = new Scanner(chunk, MinecraftClient.getInstance().world, cfg).run();
            synchronized (chunks) {
                if (found.isEmpty()) chunks.remove(key);
                else chunks.put(key, found);
            }
        });
    }

    private Config snapshot() {
        int tH0 = minTunnelHeight.getInt(), tH1 = maxTunnelHeight.getInt();
        int tW0 = minTunnelWidth.getInt(),  tW1 = maxTunnelWidth.getInt();
        int sH0 = minStairHeight.getInt(),  sH1 = maxStairHeight.getInt();
        return new Config(
                holes.isOn(), holes3x1.isOn(), tunnels.isOn(), diagonals.isOn(), stairs.isOn(), airOnly.isOn(),
                minHoleDepth.getInt(),
                minTunnelLen.getInt(), Math.min(tH0, tH1), Math.max(tH0, tH1), Math.min(tW0, tW1), Math.max(tW0, tW1),
                minDiagLen.getInt(),
                minStairLen.getInt(), Math.min(sH0, sH1), Math.max(sH0, sH1),
                Math.min(minY.getInt(), maxY.getInt()), Math.max(minY.getInt(), maxY.getInt()));
    }

    private record Config(
            boolean holes, boolean holes3x1, boolean tunnels, boolean diagonal, boolean stairs, boolean airOnly,
            int minHoleDepth,
            int minTunnelLen, int minTunnelH, int maxTunnelH, int minTunnelW, int maxTunnelW, int minDiagLen,
            int minStairLen, int minStairH, int maxStairH,
            int minY, int maxY) {
        boolean anyEnabled() {
            return holes || holes3x1 || tunnels || diagonal || stairs;
        }
    }

    private static final class Scanner {
        private final WorldChunk chunk;
        private final ClientWorld world;
        private final Config cfg;
        private final int chunkX, chunkZ, startX, startZ, worldBottom, worldTop;
        private final BlockPos.Mutable pos = new BlockPos.Mutable();
        private final List<Detection> out = new ArrayList<>();

        Scanner(WorldChunk chunk, ClientWorld world, Config cfg) {
            this.chunk = chunk;
            this.world = world;
            this.cfg = cfg;
            ChunkPos cp = chunk.getPos();
            this.chunkX = cp.x;
            this.chunkZ = cp.z;
            this.startX = cp.getStartX();
            this.startZ = cp.getStartZ();
            this.worldBottom = chunk.getBottomY();
            this.worldTop = worldBottom + chunk.getHeight();
        }

        List<Detection> run() {
            int scanMin = Math.max(cfg.minY, worldBottom);
            int scanMax = Math.min(cfg.maxY, worldTop - 1);
            if (scanMin > scanMax) return out;

            ChunkSection[] sections = chunk.getSectionArray();
            for (int si = 0; si < sections.length; si++) {
                ChunkSection section = sections[si];

                if (section == null || section.isEmpty()) continue;
                int baseY = worldBottom + (si << 4);
                if (baseY > scanMax || baseY + 15 < scanMin) continue;

                for (int ly = 0; ly < 16; ly++) {
                    int y = baseY + ly;
                    if (y < scanMin || y > scanMax) continue;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int wx = startX + lx, wz = startZ + lz;
                            if (!candidate(section.getBlockState(lx, ly, lz), wx, y, wz)) continue;
                            detect(wx, y, wz);
                        }
                    }
                }
            }
            return out;
        }

        private boolean candidate(BlockState s, int wx, int wy, int wz) {

            if (s.isOf(Blocks.CAVE_AIR) || s.isOf(Blocks.VOID_AIR)) return false;
            if (cfg.airOnly || world == null) return s.isOf(Blocks.AIR);
            return s.getCollisionShape(world, pos.set(wx, wy, wz)).isEmpty();
        }

        private void detect(int x, int y, int z) {
            if (cfg.holes)    hole1x1(x, y, z);
            if (cfg.holes3x1) { hole3x1X(x, y, z); hole3x1Z(x, y, z); }
            if (cfg.tunnels)  { tunnel(x, y, z, 1, 0); tunnel(x, y, z, 0, 1); }
            if (cfg.diagonal) { diagonal(x, y, z, 1, 1); diagonal(x, y, z, 1, -1); }
            if (cfg.stairs)   { stairs(x, y, z, 1, 0); stairs(x, y, z, -1, 0);
                                stairs(x, y, z, 0, 1); stairs(x, y, z, 0, -1); }
        }

        private boolean air(int x, int y, int z) {
            if (y < worldBottom || y >= worldTop) return false;
            pos.set(x, y, z);
            BlockState s = (x >> 4) == chunkX && (z >> 4) == chunkZ
                    ? chunk.getBlockState(pos)
                    : (world != null ? world.getBlockState(pos) : null);
            if (s == null) return false;
            if (s.isOf(Blocks.CAVE_AIR) || s.isOf(Blocks.VOID_AIR)) return false;
            if (cfg.airOnly || world == null) return s.isOf(Blocks.AIR);
            return s.getCollisionShape(world, pos).isEmpty();
        }

        private boolean solid(int x, int y, int z) {
            return !air(x, y, z);
        }

        private boolean walls1(int x, int y, int z) {
            return solid(x + 1, y, z) && solid(x - 1, y, z) && solid(x, y, z + 1) && solid(x, y, z - 1);
        }

        private void hole1x1(int x, int y, int z) {
            if (!solid(x, y - 1, z)) return;
            if (!walls1(x, y, z)) return;
            int depth = 0;
            while (air(x, y + depth, z) && walls1(x, y + depth, z)) depth++;
            if (depth < cfg.minHoleDepth) return;
            out.add(new Detection(new Box(x, y, z, x + 1, y + depth, z + 1), HOLE_1X1));
        }

        private boolean row3X(int x, int y, int z) {
            return air(x, y, z) && air(x + 1, y, z) && air(x + 2, y, z)
                    && solid(x - 1, y, z) && solid(x + 3, y, z)
                    && solid(x, y, z - 1) && solid(x + 1, y, z - 1) && solid(x + 2, y, z - 1)
                    && solid(x, y, z + 1) && solid(x + 1, y, z + 1) && solid(x + 2, y, z + 1);
        }

        private void hole3x1X(int x, int y, int z) {
            if (!solid(x - 1, y, z)) return;
            if (!row3X(x, y, z)) return;
            if (!solid(x, y - 1, z) || !solid(x + 1, y - 1, z) || !solid(x + 2, y - 1, z)) return;
            int depth = 0;
            while (row3X(x, y + depth, z)) depth++;
            if (depth < cfg.minHoleDepth) return;
            out.add(new Detection(new Box(x, y, z, x + 3, y + depth, z + 1), HOLE_3X1));
        }

        private boolean row3Z(int x, int y, int z) {
            return air(x, y, z) && air(x, y, z + 1) && air(x, y, z + 2)
                    && solid(x, y, z - 1) && solid(x, y, z + 3)
                    && solid(x - 1, y, z) && solid(x - 1, y, z + 1) && solid(x - 1, y, z + 2)
                    && solid(x + 1, y, z) && solid(x + 1, y, z + 1) && solid(x + 1, y, z + 2);
        }

        private void hole3x1Z(int x, int y, int z) {
            if (!solid(x, y, z - 1)) return;
            if (!row3Z(x, y, z)) return;
            if (!solid(x, y - 1, z) || !solid(x, y - 1, z + 1) || !solid(x, y - 1, z + 2)) return;
            int depth = 0;
            while (row3Z(x, y + depth, z)) depth++;
            if (depth < cfg.minHoleDepth) return;
            out.add(new Detection(new Box(x, y, z, x + 1, y + depth, z + 3), HOLE_3X1));
        }

        private void tunnel(int x, int y, int z, int dx, int dz) {
            int wx = dz, wz = dx;
            if (!solid(x, y - 1, z)) return;
            if (!solid(x - wx, y, z - wz)) return;
            if (!solid(x - dx, y, z - dz)) return;

            int h = 0;
            while (air(x, y + h, z)) { h++; if (h > cfg.maxTunnelH) return; }
            if (h < cfg.minTunnelH) return;

            int w = 0;
            while (air(x + wx * w, y, z + wz * w)) { w++; if (w > cfg.maxTunnelW) return; }
            if (w < cfg.minTunnelW) return;

            if (!cross(x, y, z, dx, dz, wx, wz, w, h)) return;
            int len = 0;
            while (cross(x + dx * len, y, z + dz * len, dx, dz, wx, wz, w, h)) len++;
            if (len < cfg.minTunnelLen) return;

            out.add(new Detection(new Box(
                    x, y, z,
                    x + dx * len + wx * w,
                    y + h,
                    z + dz * len + wz * w), TUNNEL));
        }

        private boolean cross(int x, int y, int z, int dx, int dz, int wx, int wz, int w, int h) {
            for (int i = 0; i < w; i++) {
                int cx = x + wx * i, cz = z + wz * i;
                if (!solid(cx, y - 1, cz)) return false;
                if (!solid(cx, y + h, cz)) return false;
                for (int dy = 0; dy < h; dy++) if (!air(cx, y + dy, cz)) return false;
            }
            for (int dy = 0; dy < h; dy++) {
                if (!solid(x - wx, y + dy, z - wz)) return false;
                if (!solid(x + wx * w, y + dy, z + wz * w)) return false;
            }
            return true;
        }

        private boolean diagSection(int x, int y, int z) {
            if (!air(x, y, z)) return false;
            if (!solid(x, y - 1, z)) return false;
            for (int k = 0; k < cfg.minTunnelH; k++) if (!air(x, y + k, z)) return false;

            int n = 0;
            if (air(x + 1, y, z)) n++;
            if (air(x - 1, y, z)) n++;
            if (air(x, y, z + 1)) n++;
            if (air(x, y, z - 1)) n++;
            return n <= 2;
        }

        private void diagonal(int x, int y, int z, int dx, int dz) {
            if (!diagSection(x, y, z)) return;
            if (!solid(x - dx, y, z)) return;
            if (!solid(x, y, z - dz)) return;

            List<int[]> cells = new ArrayList<>();
            int cx = x, cz = z, steps = 1;
            cells.add(new int[]{cx, cz});
            boolean moveX = diagSection(cx + dx, y, cz);
            while (steps < 1024) {
                int nx = moveX ? cx + dx : cx;
                int nz = moveX ? cz : cz + dz;
                if (!diagSection(nx, y, nz)) break;
                cx = nx; cz = nz; steps++;
                cells.add(new int[]{cx, cz});
                moveX = !moveX;
            }
            if (steps < cfg.minDiagLen) return;

            for (int[] c : cells) {
                int h = 0;
                while (h < cfg.maxTunnelH && air(c[0], y + h, c[1])) h++;
                out.add(new Detection(new Box(c[0], y, c[1], c[0] + 1, y + h, c[1] + 1), DIAGONAL));
            }
        }

        private boolean stairSection(int x, int y, int z, int dx, int dz) {
            if (!air(x, y, z)) return false;
            if (!solid(x, y - 1, z)) return false;
            for (int k = 0; k < cfg.minStairH; k++) {
                if (!air(x, y + k, z)) return false;

                if (dx != 0) { if (!solid(x, y + k, z - 1) || !solid(x, y + k, z + 1)) return false; }
                else         { if (!solid(x - 1, y + k, z) || !solid(x + 1, y + k, z)) return false; }
            }
            return true;
        }

        private void stairs(int x, int y, int z, int dx, int dz) {
            if (!stairSection(x, y, z, dx, dz)) return;
            if (stairSection(x - dx, y - 1, z - dz, dx, dz)) return;

            List<int[]> steps = new ArrayList<>();
            int i = 0;
            while (i < 256 && stairSection(x + dx * i, y + i, z + dz * i, dx, dz)) {
                steps.add(new int[]{x + dx * i, y + i, z + dz * i});
                i++;
            }
            if (steps.size() < cfg.minStairLen) return;

            for (int[] s : steps) {
                int h = 0;
                while (h < cfg.maxStairH && air(s[0], s[1] + h, s[2])) h++;
                out.add(new Detection(new Box(s[0], s[1], s[2], s[0] + 1, s[1] + h, s[2] + 1), STAIRS));
            }
        }
    }
}
