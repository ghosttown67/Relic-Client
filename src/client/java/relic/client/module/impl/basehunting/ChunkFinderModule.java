package relic.client.module.impl.basehunting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import relic.client.api.render.BoxRenderer;
import relic.client.api.render.WorldToScreen;
import relic.client.gui.theme.ThemeManager;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;
import relic.client.module.setting.Setting;
import relic.client.notification.NotificationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkFinderModule extends Module {

    private static final int T_IRREGULAR = 0;
    private static final int T_ROTATED   = 1;
    private static final int T_COBBLED   = 2;
    private static final int T_AIR       = 3;
    private static final int TYPE_COUNT  = 4;

    private static final String[] TYPE_NAMES = {
            "Irregular Deepslate", "Rotated Deepslate", "Cobbled Deepslate", "Air Pocket"};

    private static final float[][] TYPE_COLORS = {
            {1.0f, 0.55f, 0.1f},
            {1.0f, 0.2f,  0.2f},
            {1.0f, 0.9f,  0.2f},
            {0.3f, 0.8f,  1.0f},
    };

    private static final int[] TYPE_TEXT = {
            argb(TYPE_COLORS[0]), argb(TYPE_COLORS[1]), argb(TYPE_COLORS[2]), argb(TYPE_COLORS[3])};

    private static final int  NOTIFY_ACCENT = 0xFFB347FF;
    private static final long NOTIFY_COOLDOWN_MS = 3000L;

    private static final int SHELL_RADIUS = 5;

    private final BooleanSetting irregular = new BooleanSetting("Irregular Deepslate", true);
    private final BooleanSetting rotated   = new BooleanSetting("Rotated Deepslate", true);
    private final BooleanSetting cobbled    = new BooleanSetting("Cobbled Deepslate", true);
    private final BooleanSetting airPockets = new BooleanSetting("Air Pockets", true);
    private final BooleanSetting hiddenOnly = new BooleanSetting("Hidden Only", false);

    private final NumberSetting  maxDeepslate = new NumberSetting("Max Detected", 4, 1, 64, true);
    private final NumberSetting  minY = new NumberSetting("Min Y", 16, -64, 320, true);
    private final NumberSetting  maxY = new NumberSetting("Max Y", 48, -64, 320, true);
    private final ModeSetting    renderMode = new ModeSetting("Render", "Both", "Both", "Filled", "Outline");
    private final BooleanSetting chunkHighlight = new BooleanSetting("Chunk Highlight", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final BooleanSetting notify = new BooleanSetting("Notify", true);

    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<BoxRenderer.ColoredBox>> chunks =
            new Long2ObjectOpenHashMap<>();

    private final Long2ObjectOpenHashMap<int[]> chunkCounts = new Long2ObjectOpenHashMap<>();

    private final Set<Long> notified = ConcurrentHashMap.newKeySet();
    private final Object notifyLock = new Object();
    private volatile long lastNotifyTime;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Relic-ChunkFinder-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private RegistryKey<World> lastDimension;

    public ChunkFinderModule() {
        super("ChunkFinder", "Highlights chunk anomalies left by player bases", Category.BASE_HUNTING);
        addSettings(irregular, rotated, cobbled, airPockets, hiddenOnly, maxDeepslate, minY, maxY,
                renderMode, chunkHighlight, labels, notify);

        Setting<?>[] rescanTriggers = {irregular, rotated, cobbled, airPockets, hiddenOnly, maxDeepslate, minY, maxY};
        for (Setting<?> setting : rescanTriggers) {
            setting.onChanged(this::fullRescan);
        }

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled()) scanChunkAsync(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            synchronized (chunks) {
                chunks.remove(key);
                chunkCounts.remove(key);
            }
            notified.remove(key);
        });
    }

    @Override
    protected void onEnable() {
        fullRescan();
    }

    @Override
    protected void onDisable() {
        synchronized (chunks) {
            chunks.clear();
            chunkCounts.clear();
        }
        notified.clear();
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
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        boolean highlight = chunkHighlight.isOn();
        int from = Math.min(minY.getInt(), maxY.getInt());
        int to = Math.max(minY.getInt(), maxY.getInt());

        int accent = ThemeManager.get().accent();
        float cr = ((accent >> 16) & 0xFF) / 255f;
        float cg = ((accent >> 8) & 0xFF) / 255f;
        float cb = (accent & 0xFF) / 255f;

        List<BoxRenderer.ColoredBox> list = new ArrayList<>();
        synchronized (chunks) {
            for (var entry : chunks.long2ObjectEntrySet()) {
                list.addAll(entry.getValue().values());
                if (highlight) {
                    ChunkPos cp = new ChunkPos(entry.getLongKey());
                    Box column = new Box(cp.getStartX(), from, cp.getStartZ(),
                            cp.getStartX() + 16, to + 1, cp.getStartZ() + 16);
                    list.add(new BoxRenderer.ColoredBox(column, cr, cg, cb, 0.5f));
                }
            }
        }
        BoxRenderer.draw(list, resolveMode());
    }

    private BoxRenderer.Mode resolveMode() {
        return switch (renderMode.getValue()) {
            case "Filled"  -> BoxRenderer.Mode.FILLED;
            case "Outline" -> BoxRenderer.Mode.OUTLINED;
            default        -> BoxRenderer.Mode.BOTH;
        };
    }

    private record Flagged(long key, int[] counts) {}

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (!labels.isOn()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        List<Flagged> flagged = new ArrayList<>();
        synchronized (chunks) {
            for (var entry : chunkCounts.long2ObjectEntrySet()) {
                flagged.add(new Flagged(entry.getLongKey(), entry.getValue()));
            }
        }
        if (flagged.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int line = tr.fontHeight + 1;
        float scale = 0.75f;

        double anchorY = Math.max(minY.getInt(), maxY.getInt()) + 1.5;

        final double maxLabelDistSq = 128.0 * 128.0;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        for (Flagged f : flagged) {
            ChunkPos cp = new ChunkPos(f.key());
            double centerX = cp.getStartX() + 8.0;
            double centerZ = cp.getStartZ() + 8.0;
            double dx = centerX - px;
            double dz = centerZ - pz;
            if (dx * dx + dz * dz > maxLabelDistSq) continue;

            Vec3d anchor = new Vec3d(centerX, anchorY, centerZ);
            float[] screen = WorldToScreen.project(anchor);
            if (screen == null) continue;

            int present = 0;
            for (int t = 0; t < TYPE_COUNT; t++) if (f.counts()[t] > 0) present++;
            if (present == 0) continue;

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(scale, scale);
            float cx = screen[0] / scale;

            float y = screen[1] / scale - present * line;
            for (int t = 0; t < TYPE_COUNT; t++) {
                int count = f.counts()[t];
                if (count == 0) continue;
                String label = TYPE_NAMES[t] + (count > 1 ? "  x" + count : "");
                drawCentered(context, tr, label, cx, y, TYPE_TEXT[t]);
                y += line;
            }
            matrices.popMatrix();
        }
    }

    private void drawCentered(DrawContext context, TextRenderer tr, String text, float centerX, float y, int color) {
        int w = tr.getWidth(text);
        int x = (int) (centerX - w / 2.0f);
        int iy = (int) y;
        context.fill(x - 2, iy - 1, x + w + 2, iy + tr.fontHeight, 0x90000000);
        context.drawText(tr, text, x, iy, color, true);
    }

    private void fullRescan() {
        if (!isEnabled()) return;
        synchronized (chunks) {
            chunks.clear();
            chunkCounts.clear();
        }
        notified.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        int viewDistance = mc.options.getViewDistance().getValue();
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                Chunk chunk = mc.world.getChunkManager()
                        .getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk worldChunk) {
                    scanChunkAsync(worldChunk);
                }
            }
        }
    }

    private void scanChunkAsync(WorldChunk chunk) {

        boolean wantIrregular = irregular.isOn();
        boolean wantRotated   = rotated.isOn();
        boolean wantCobbled   = cobbled.isOn();
        boolean wantAir       = airPockets.isOn();
        boolean enclosedOnly  = hiddenOnly.isOn();
        int     maxDeep       = maxDeepslate.getInt();
        boolean wantNotify    = notify.isOn();
        int from = Math.min(minY.getInt(), maxY.getInt());
        int to   = Math.max(minY.getInt(), maxY.getInt());

        if (!wantIrregular && !wantRotated && !wantCobbled && !wantAir) {
            long key = chunk.getPos().toLong();
            synchronized (chunks) {
                chunks.remove(key);
                chunkCounts.remove(key);
            }
            return;
        }

        WORKER.submit(() -> {
            if (!isEnabled()) return;

            ClientWorld world = MinecraftClient.getInstance().world;

            int bottom = chunk.getBottomY();
            int topExcl = bottom + chunk.getHeight();
            int scanMin = Math.max(from, bottom);
            int scanMax = Math.min(to, topExcl - 1);

            ChunkPos pos = chunk.getPos();
            int startX = pos.getStartX();
            int startZ = pos.getStartZ();

            Long2ObjectOpenHashMap<BoxRenderer.ColoredBox> found = new Long2ObjectOpenHashMap<>();
            int[] counts = new int[TYPE_COUNT];
            int deepslateFound = 0;
            BlockPos.Mutable main = new BlockPos.Mutable();
            BlockPos.Mutable probe = new BlockPos.Mutable();

            for (int y = scanMin; y <= scanMax; y++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = startX + lx;
                        int wz = startZ + lz;
                        BlockState state = chunk.getBlockState(main.set(wx, y, wz));

                        int type = classify(state, wantIrregular, wantRotated, wantCobbled);
                        if (type >= 0) {
                            if (enclosedOnly
                                    && !allNeighborsSolid(chunk, probe, wx, y, wz, bottom, topExcl)) {
                                continue;
                            }
                            if (deepslateFound >= maxDeep) continue;
                            deepslateFound++;
                        } else if (wantAir && world != null && state.isOf(Blocks.AIR)
                                && isSealedHole(world, probe, wx, y, wz)) {

                            type = T_AIR;
                        }

                        if (type >= 0) {
                            float[] c = TYPE_COLORS[type];
                            found.put(BlockPos.asLong(wx, y, wz), new BoxRenderer.ColoredBox(
                                    new Box(new BlockPos(wx, y, wz)), c[0], c[1], c[2], 1.0f));
                            counts[type]++;
                        }
                    }
                }
            }

            long key = pos.toLong();
            synchronized (chunks) {
                if (found.isEmpty()) {
                    chunks.remove(key);
                    chunkCounts.remove(key);
                } else {
                    chunks.put(key, found);
                    chunkCounts.put(key, counts);
                }
            }

            if (wantNotify && !found.isEmpty()) {
                maybeNotify(key, found.size(), pos);
            }
        });
    }

    private void maybeNotify(long chunkKey, int count, ChunkPos pos) {
        synchronized (notifyLock) {
            if (!notified.add(chunkKey)) return;
            long now = System.currentTimeMillis();
            if (now - lastNotifyTime < NOTIFY_COOLDOWN_MS) return;
            lastNotifyTime = now;
        }
        String where = (pos.getStartX() + 8) + ", " + (pos.getStartZ() + 8);
        NotificationManager.getInstance().push("ChunkFinder",
                count + " anomalies near " + where, NOTIFY_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
    }

    private int classify(BlockState state, boolean wantIrregular, boolean wantRotated, boolean wantCobbled) {
        if (state.isOf(Blocks.DEEPSLATE)) {
            boolean isRotated = state.contains(Properties.AXIS)
                    && state.get(Properties.AXIS) != Direction.Axis.Y;
            if (isRotated && wantRotated) return T_ROTATED;
            if (wantIrregular) return T_IRREGULAR;
            return -1;
        }
        if (state.isOf(Blocks.COBBLED_DEEPSLATE) && wantCobbled) {
            return T_COBBLED;
        }
        return -1;
    }

    private static int argb(float[] c) {
        return 0xFF000000
                | ((int) (c[0] * 255) << 16)
                | ((int) (c[1] * 255) << 8)
                | (int) (c[2] * 255);
    }

    private boolean isSealedHole(ClientWorld world, BlockPos.Mutable p, int x, int y, int z) {

        for (Direction d : Direction.values()) {
            if (!isSolidWorld(world, p, x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ())) {
                return false;
            }
        }
        for (int r = 1; r <= SHELL_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {

                        if (Math.abs(dx) != r && Math.abs(dy) != r && Math.abs(dz) != r) continue;
                        if (!isSolidWorld(world, p, x + dx, y + dy, z + dz)) return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isSolidWorld(ClientWorld world, BlockPos.Mutable p, int x, int y, int z) {
        BlockState s = world.getBlockState(p.set(x, y, z));
        return s.isSolidBlock(world, p);
    }

    private boolean allNeighborsSolid(WorldChunk chunk, BlockPos.Mutable p,
                                      int x, int y, int z, int bottom, int topExcl) {
        return isSolid(chunk, p, x + 1, y, z, bottom, topExcl)
                && isSolid(chunk, p, x - 1, y, z, bottom, topExcl)
                && isSolid(chunk, p, x, y + 1, z, bottom, topExcl)
                && isSolid(chunk, p, x, y - 1, z, bottom, topExcl)
                && isSolid(chunk, p, x, y, z + 1, bottom, topExcl)
                && isSolid(chunk, p, x, y, z - 1, bottom, topExcl);
    }

    private boolean isSolid(WorldChunk chunk, BlockPos.Mutable p,
                            int wx, int wy, int wz, int bottom, int topExcl) {
        if (wy < bottom || wy >= topExcl) return false;
        int lx = wx - chunk.getPos().getStartX();
        int lz = wz - chunk.getPos().getStartZ();
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return false;
        BlockState s = chunk.getBlockState(p.set(wx, wy, wz));
        return !s.isAir() && s.getFluidState().isEmpty();
    }
}
